/*
 * Copyright (C) 2015 SoftIndex LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.datakernel.aggregation;

import io.datakernel.aggregation.ot.AggregationStructure;
import io.datakernel.async.Stages;
import io.datakernel.codegen.DefiningClassLoader;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.eventloop.EventloopService;
import io.datakernel.file.AsyncFile;
import io.datakernel.jmx.EventloopJmxMBean;
import io.datakernel.jmx.JmxAttribute;
import io.datakernel.jmx.JmxOperation;
import io.datakernel.jmx.StageStats;
import io.datakernel.serializer.BufferSerializer;
import io.datakernel.stream.StreamConsumerWithResult;
import io.datakernel.stream.StreamConsumers;
import io.datakernel.stream.StreamProducerWithResult;
import io.datakernel.stream.StreamProducers;
import io.datakernel.stream.file.StreamFileReader;
import io.datakernel.stream.file.StreamFileWriter;
import io.datakernel.stream.processor.StreamBinaryDeserializer;
import io.datakernel.stream.processor.StreamBinarySerializer;
import io.datakernel.stream.processor.StreamLZ4Compressor;
import io.datakernel.stream.processor.StreamLZ4Decompressor;
import io.datakernel.util.MemSize;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static io.datakernel.jmx.ValueStats.SMOOTHING_WINDOW_5_MINUTES;
import static io.datakernel.stream.DataStreams.stream;
import static java.nio.file.StandardOpenOption.READ;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Stores aggregation chunks in local file system.
 */
public class LocalFsChunkStorage implements AggregationChunkStorageWithBackup, EventloopService, EventloopJmxMBean {
	public static final double DEFAULT_SMOOTHING_WINDOW = SMOOTHING_WINDOW_5_MINUTES;
	public static final String DEFAULT_BACKUP_FOLDER_NAME = "backups";
	public static final String LOG = ".log";
	public static final String TEMP_LOG = ".temp";

	private final Logger logger = getLogger(this.getClass());

	private final Eventloop eventloop;
	private final ExecutorService executorService;
	private final IdGenerator<Long> idGenerator;

	private final StageStats stageIdGenerator = StageStats.create(DEFAULT_SMOOTHING_WINDOW);
	private final StageStats stageOpenR1 = StageStats.create(DEFAULT_SMOOTHING_WINDOW);
	private final StageStats stageOpenR2 = StageStats.create(DEFAULT_SMOOTHING_WINDOW);
	private final StageStats stageOpenR3 = StageStats.create(DEFAULT_SMOOTHING_WINDOW);
	private final StageStats stageOpenW = StageStats.create(DEFAULT_SMOOTHING_WINDOW);
	private final StageStats stageFinishChunks = StageStats.create(DEFAULT_SMOOTHING_WINDOW);
	private final StageStats stageList = StageStats.create(DEFAULT_SMOOTHING_WINDOW);
	private final StageStats stageBackup = StageStats.create(DEFAULT_SMOOTHING_WINDOW);
	private final StageStats stageCleanup = StageStats.create(DEFAULT_SMOOTHING_WINDOW);

	private final Path dir;

	private Path backupPath;
	private int bufferSize = 1024 * 1024;

	/**
	 * Constructs an aggregation storage, that runs in the specified event loop, performs blocking IO in the given executor,
	 * serializes records according to specified aggregation structure and stores data in the given directory.
	 *
	 * @param eventloop       event loop, in which aggregation storage is to run
	 * @param executorService executor, where blocking IO operations are to be run
	 * @param idGenerator
	 * @param dir             directory where data is saved
	 */
	private LocalFsChunkStorage(Eventloop eventloop, ExecutorService executorService, IdGenerator<Long> idGenerator, Path dir, Path backUpPath) {
		this.eventloop = eventloop;
		this.executorService = executorService;
		this.dir = dir;
		this.idGenerator = idGenerator;
		this.backupPath = backUpPath;
	}

	public static LocalFsChunkStorage create(Eventloop eventloop, ExecutorService executorService, IdGenerator<Long> idGenerator, Path dir) {
		return new LocalFsChunkStorage(eventloop, executorService, idGenerator, dir, dir.resolve(DEFAULT_BACKUP_FOLDER_NAME + "/"));
	}

	public LocalFsChunkStorage withBufferSize(int bufferSize) {
		this.bufferSize = bufferSize;
		return this;
	}

	public LocalFsChunkStorage withBufferSize(MemSize bufferSize) {
		this.bufferSize = (int) bufferSize.get();
		return this;
	}

	public LocalFsChunkStorage withBackupPath(Path backupPath) {
		this.backupPath = backupPath;
		return this;
	}

	@Override
	public <T> CompletionStage<StreamProducerWithResult<T, Void>> read(AggregationStructure aggregation, List<String> fields,
	                                                                   Class<T> recordClass, long id, DefiningClassLoader classLoader) {
		return Stages.firstComplete(
				() -> stageOpenR1.monitor(AsyncFile.openAsync(executorService, dir.resolve(id + LOG), new OpenOption[]{READ})),
				() -> stageOpenR2.monitor(AsyncFile.openAsync(executorService, dir.resolve(id + TEMP_LOG), new OpenOption[]{READ})),
				() -> stageOpenR3.monitor(AsyncFile.openAsync(executorService, dir.resolve(id + LOG), new OpenOption[]{READ})))
				.thenApply(file -> {
					StreamFileReader fileReader = StreamFileReader.readFileFrom(file, bufferSize, 0L);

					StreamLZ4Decompressor decompressor = StreamLZ4Decompressor.create();
					stream(fileReader, decompressor.getInput());

					BufferSerializer<T> bufferSerializer = AggregationUtils.createBufferSerializer(aggregation, recordClass,
							aggregation.getKeys(), fields, classLoader);
					StreamBinaryDeserializer<T> deserializer = StreamBinaryDeserializer.create(bufferSerializer);

					stream(decompressor.getOutput(), deserializer.getInput());
					return StreamProducers.withEndOfStreamAsResult(deserializer.getOutput());
				});
	}

	@Override
	public <T> CompletionStage<StreamConsumerWithResult<T, Void>> write(AggregationStructure aggregation, List<String> fields,
	                                                                    Class<T> recordClass, long id,
	                                                                    DefiningClassLoader classLoader) {
		BufferSerializer<T> bufferSerializer = AggregationUtils.createBufferSerializer(aggregation, recordClass,
				aggregation.getKeys(), fields, classLoader);
		return stageOpenW.monitor(AsyncFile.openAsync(executorService, dir.resolve(id + TEMP_LOG), StreamFileWriter.CREATE_OPTIONS))
				.thenApply(file -> {
					StreamBinarySerializer<T> serializer = StreamBinarySerializer.create(bufferSerializer)
							.withDefaultBufferSize(StreamBinarySerializer.MAX_SIZE_2);
					StreamLZ4Compressor compressor = StreamLZ4Compressor.fastCompressor();
					StreamFileWriter writer = StreamFileWriter.create(file, true);

					stream(serializer.getOutput(), compressor.getInput());
					stream(compressor.getOutput(), writer);

					return StreamConsumers.withResult(serializer.getInput(), writer.getFlushStage());
				});
	}

	@Override
	public CompletionStage<Long> createId() {
		return stageIdGenerator.monitor(idGenerator.createId());
	}

	@Override
	public CompletionStage<Void> finish(Set<Long> chunkIds) {
		return stageFinishChunks.monitor(eventloop.callExecutor(executorService, () -> {
			for (Long chunkId : chunkIds) {
				Path tempLog = dir.resolve(chunkId + TEMP_LOG);
				Path log = dir.resolve(chunkId + LOG);
				Files.setLastModifiedTime(tempLog, FileTime.fromMillis(System.currentTimeMillis()));
				Files.move(tempLog, log, StandardCopyOption.ATOMIC_MOVE);
			}
			return null;
		}));
	}

	public CompletionStage<Void> backup(String backupId, Set<Long> chunkIds) {
		return stageBackup.monitor(eventloop.callExecutor(executorService, () -> {
			Path tempBackupDir = backupPath.resolve(backupId + "_tmp/");
			Files.createDirectories(tempBackupDir);
			for (long chunkId : chunkIds) {
				Path target = dir.resolve(chunkId + LOG).toAbsolutePath();
				Path link = tempBackupDir.resolve(chunkId + LOG).toAbsolutePath();
				Files.createLink(link, target);
			}

			Path backupDir = backupPath.resolve(backupId + "/");
			Files.move(tempBackupDir, backupDir, StandardCopyOption.ATOMIC_MOVE);
			return null;
		}));
	}

	public CompletionStage<Void> cleanup(Set<Long> saveChunks) {
		return cleanupBeforeTimestamp(saveChunks, -1);
	}

	public CompletionStage<Void> cleanupBeforeTimestamp(Set<Long> saveChunks, long timestamp) {
		return stageCleanup.monitor(eventloop.callExecutor(executorService, () -> {
			logger.trace("Cleanup before timestamp, save chunks size: {}, timestamp {}", saveChunks.size(), timestamp);
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
				List<Path> filesToDelete = new ArrayList<>();

				for (Path file : stream) {
					if (!file.toString().endsWith(LOG)) {
						continue;
					}
					long id;
					try {
						String filename = file.getFileName().toString();
						id = Long.parseLong((filename.substring(0, filename.length() - LOG.length())));
					} catch (NumberFormatException e) {
						logger.warn("Invalid chunk filename: " + file);
						continue;
					}
					if (saveChunks.contains(id)) continue;
					FileTime lastModifiedTime = Files.getLastModifiedTime(file);
					if (timestamp != -1 && lastModifiedTime.toMillis() > timestamp) {
						logger.warn("File {} timestamp {} > {}",
								file, lastModifiedTime.toMillis(), timestamp);
						continue;
					}

					filesToDelete.add(file);
				}

				for (Path file : filesToDelete) {
					try {
						if (logger.isTraceEnabled()) {
							FileTime lastModifiedTime = Files.getLastModifiedTime(file);
							logger.trace("Delete file: {} with last modifiedTime: {}({} millis)", file,
									lastModifiedTime, lastModifiedTime.toMillis());
						}
						Files.delete(file);
					} catch (IOException e) {
						logger.warn("Could not delete file: " + file);
					}
				}
			}
			return null;
		}));
	}

	public CompletionStage<Set<Long>> list(Predicate<String> filter, Predicate<Long> lastModified) {
		return stageList.monitor(eventloop.callExecutor(executorService, () -> {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
				return StreamSupport.stream(stream.spliterator(), false)
						.filter(file -> lastModifiedFilter(lastModified, file))
						.map(file -> file.getFileName().toString())
						.filter(name -> name.endsWith(LOG) && filter.test(name))
						.map(name -> Long.parseLong(name.substring(0, name.length() - LOG.length())))
						.collect(Collectors.toSet());
			}
		}));
	}

	private boolean lastModifiedFilter(Predicate<Long> lastModified, Path file) {
		try {
			return lastModified.test(Files.getLastModifiedTime(file).toMillis());
		} catch (IOException e) {
			return false;
		}
	}

	@Override
	public Eventloop getEventloop() {
		return eventloop;
	}

	@Override
	public CompletionStage<Void> start() {
		return eventloop.callExecutor(executorService, () -> Files.createDirectories(dir))
				.thenApply(t -> null);
	}

	@Override
	public CompletionStage<Void> stop() {
		return Stages.of(null);
	}

	@JmxAttribute
	public StageStats getStageIdGenerator() {
		return stageIdGenerator;
	}

	@JmxAttribute
	public StageStats getStageFinishChunks() {
		return stageFinishChunks;
	}

	@JmxAttribute
	public StageStats getStageBackup() {
		return stageBackup;
	}

	@JmxAttribute
	public StageStats getStageCleanup() {
		return stageCleanup;
	}

	@JmxAttribute
	public StageStats getStageList() {
		return stageList;
	}

	@JmxAttribute
	public StageStats getStageOpenR1() {
		return stageOpenR1;
	}

	@JmxAttribute
	public StageStats getStageOpenR2() {
		return stageOpenR2;
	}

	@JmxAttribute
	public StageStats getStageOpenR3() {
		return stageOpenR3;
	}

	@JmxAttribute
	public StageStats getStageOpenW() {
		return stageOpenW;
	}

	@JmxOperation
	public void resetStats() {
		stageIdGenerator.resetStats();
		stageOpenR1.resetStats();
		stageOpenR2.resetStats();
		stageOpenR3.resetStats();
		stageOpenW.resetStats();
		stageFinishChunks.resetStats();
		stageList.resetStats();
		stageBackup.resetStats();
		stageCleanup.resetStats();
	}
}
