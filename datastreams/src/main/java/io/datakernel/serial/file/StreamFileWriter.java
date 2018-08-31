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

package io.datakernel.serial.file;

import io.datakernel.async.SettableStage;
import io.datakernel.async.Stage;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.file.AsyncFile;
import io.datakernel.serial.SerialConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

import static java.nio.file.StandardOpenOption.*;

/**
 * This consumer allows you to asynchronously write binary data to a file.
 */
public final class StreamFileWriter implements SerialConsumer<ByteBuf> {
	private static final Logger logger = LoggerFactory.getLogger(StreamFileWriter.class);

	public static final OpenOption[] CREATE_OPTIONS = new OpenOption[]{WRITE, CREATE_NEW, APPEND};

	private final AsyncFile asyncFile;
	private final SettableStage<Void> flushStage = new SettableStage<>();

	private boolean forceOnClose = false;
	private boolean forceMetadata = false;
	private long startingOffset = -1;
	private boolean started;

	// region creators
	private StreamFileWriter(AsyncFile asyncFile) {
		this.asyncFile = asyncFile;
	}

	public static StreamFileWriter create(ExecutorService executor, Path path) throws IOException {
		return create(AsyncFile.open(executor, path, CREATE_OPTIONS));
	}

	public static StreamFileWriter create(AsyncFile asyncFile) {
		return new StreamFileWriter(asyncFile);
	}

	public StreamFileWriter withForceOnClose(boolean forceMetadata) {
		forceOnClose = true;
		this.forceMetadata = forceMetadata;
		return this;
	}

	public StreamFileWriter withOffset(long offset) {
		startingOffset = offset;
		return this;
	}
	// endregion

	public Stage<Void> getFlushStage() {
		return flushStage;
	}

	@Override
	public void closeWithError(Throwable e) {
		// TODO
	}

	@Override
	public Stage<Void> accept(ByteBuf buf) {
		return start() // TODO
				.whenException(e -> buf.recycle())
				.thenCompose($ -> asyncFile.write(buf))
				.thenComposeEx(($, e) -> {
					if (e == null) {
						return Stage.complete();
					} else {
						closeWithError(e);
						return Stage.ofException(e);
					}
				});
	}

	private Stage<Void> close() {
		return (forceOnClose ? asyncFile.forceAndClose(forceMetadata) : asyncFile.close())
				.whenComplete(($, e) -> {
					if (e == null) {
						logger.trace(this + ": closed file");
					} else {
						logger.error(this + ": failed to close file", e);
					}
				});
	}

	private Stage<Void> start() {
		if (!started && startingOffset != -1) {
			return asyncFile.seek(startingOffset)
					.thenRun(() -> started = true);
		} else {
			started = true;
			return Stage.complete();
		}
	}

	@Override
	public String toString() {
		return "{" + asyncFile + "}";
	}
}
