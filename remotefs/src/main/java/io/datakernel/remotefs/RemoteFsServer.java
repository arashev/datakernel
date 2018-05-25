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

package io.datakernel.remotefs;

import io.datakernel.async.Stage;
import io.datakernel.async.Stages;
import io.datakernel.eventloop.AbstractServer;
import io.datakernel.eventloop.AsyncTcpSocket;
import io.datakernel.eventloop.AsyncTcpSocket.EventHandler;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.remotefs.RemoteFsCommands.*;
import io.datakernel.remotefs.RemoteFsResponses.FsResponse;
import io.datakernel.stream.net.Messaging;
import io.datakernel.stream.net.MessagingSerializer;
import io.datakernel.stream.net.MessagingWithBinaryStreaming;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.datakernel.stream.net.MessagingSerializers.ofJson;

public final class RemoteFsServer extends AbstractServer<RemoteFsServer> {
	protected final FileManager fileManager;
	private final MessagingSerializer<FsCommand, FsResponse> serializer = ofJson(RemoteFsCommands.ADAPTER, RemoteFsResponses.ADAPTER);

	private final Map<Class, MessagingHandler> handlers;

	// region creators & builder methods
	private RemoteFsServer(Eventloop eventloop, FileManager fileManager) {
		super(eventloop);
		this.fileManager = fileManager;
		this.handlers = createHandlers();
	}

	public static RemoteFsServer create(Eventloop eventloop, ExecutorService executor, Path storage) {
		return new RemoteFsServer(eventloop, FileManager.create(eventloop, executor, storage));
	}
	// endregion

	// set up connection
	@Override
	protected final EventHandler createSocketHandler(AsyncTcpSocket asyncTcpSocket) {
		MessagingWithBinaryStreaming<FsCommand, FsResponse> messaging =
			MessagingWithBinaryStreaming.create(asyncTcpSocket, serializer);
		messaging.setTag(this);
		messaging.receive()
			.whenResult(msg -> {
				if (msg != null) {
					logger.trace("received {}", msg);
					doRead(messaging, msg);
				} else {
					logger.warn("unexpected end of stream");
					messaging.close();
				}
			})
			.whenException(e -> {
				logger.error("received error while reading", e);
				messaging.close();
			});
		return messaging;
	}

	private void doRead(Messaging<FsCommand, FsResponse> messaging, FsCommand item) {
		MessagingHandler handler = handlers.get(item.getClass());
		if (handler == null) {
			messaging.close();
			logger.error("missing handler for " + item);
		} else {
			//noinspection unchecked
			handler.onMessage(messaging, item);
		}
	}

	protected interface MessagingHandler<I, O> {
		void onMessage(Messaging<I, O> messaging, I item);
	}

	private Map<Class, MessagingHandler> createHandlers() {
		Map<Class, MessagingHandler> map = new HashMap<>();
		map.put(Upload.class, new UploadMessagingHandler());
		map.put(Download.class, new DownloadMessagingHandler());
		map.put(Delete.class, new DeleteMessagingHandler());
		map.put(ListFiles.class, new ListFilesMessagingHandler());
		map.put(Move.class, new MoveMessagingHandler());
		return map;
	}

	// handler classes
	private class UploadMessagingHandler implements MessagingHandler<Upload, FsResponse> {
		@Override
		public void onMessage(Messaging<Upload, FsResponse> messaging, Upload item) {
			logger.trace("uploading {}", item.getFilePath());
			fileManager.save(item.getFilePath())
				.whenResult(fileWriter -> messaging.receiveBinaryStream()
					.streamTo(
						fileWriter
							.thenRun(() -> {
								logger.trace("read all bytes for {}", item.getFilePath());
								messaging.send(new RemoteFsResponses.Acknowledge());
								messaging.sendEndOfStream();
							}))
					.getResult()
					.whenException(throwable -> messaging.close()))
				.whenException(throwable -> messaging.close());
		}
	}

	private class DownloadMessagingHandler implements MessagingHandler<Download, FsResponse> {

		private Consumer<Throwable> errorSender(Messaging<Download, FsResponse> messaging) {
			return throwable -> {
				messaging.send(new RemoteFsResponses.Err(throwable.getMessage()));
				messaging.sendEndOfStream();
			};
		}

		@Override
		public void onMessage(Messaging<Download, FsResponse> messaging, Download item) {
			fileManager.size(item.getFilePath())
				.whenException(errorSender(messaging))
				.whenResult(size -> {
					if (size == -1) {
						errorSender(messaging).accept(new Throwable("File not found"));
						return;
					}
					messaging.send(new RemoteFsResponses.Ready(size))
						.whenException(errorSender(messaging))
						.thenRun(() ->
							fileManager.get(item.getFilePath(), item.getStartPosition())
								.whenException(errorSender(messaging))
								.whenResult(fileReader ->
									fileReader.streamTo(messaging.sendBinaryStream())
										.getResult()
										.whenComplete(($, throwable) -> messaging.close())));
				});
		}
	}

	private class DeleteMessagingHandler implements MessagingHandler<Delete, FsResponse> {
		@Override
		public void onMessage(Messaging<Delete, FsResponse> messaging, Delete item) {
			fileManager.delete(item.getFilePath()).whenComplete(($, throwable) -> {
				messaging.send(throwable == null ? new RemoteFsResponses.Ok() : new RemoteFsResponses.Err(throwable.getMessage()));
				messaging.sendEndOfStream();
			});
		}
	}

	private class ListFilesMessagingHandler implements MessagingHandler<ListFiles, FsResponse> {
		@Override
		public void onMessage(Messaging<ListFiles, FsResponse> messaging, ListFiles item) {
			fileManager.scanAsync()
					.whenComplete((strings, throwable) -> {
						assert strings != null;
						messaging.send(throwable == null ? new RemoteFsResponses.ListOfFiles(strings) : new RemoteFsResponses.Err(throwable.getMessage()));
						messaging.sendEndOfStream();
					});
		}
	}

	private class MoveMessagingHandler implements MessagingHandler<Move, FsResponse> {
		@Override
		public void onMessage(Messaging<Move, FsResponse> messaging, Move item) {
			List<Stage<Void>> tasks = item.getChanges().entrySet().stream()
				.map(e -> fileManager.move(e.getKey(), e.getValue()))
				.collect(Collectors.toList());

			Stages.all(tasks).whenComplete((aVoid, throwable) -> {
				messaging.send(throwable == null ? new RemoteFsResponses.Ok() : new RemoteFsResponses.Err(throwable.getMessage()));
				messaging.sendEndOfStream();
			});
		}
	}

	@Override
	public String toString() {
		return "RemoteFsServer{" +
				"fileManager=" + fileManager +
				(listenAddresses.isEmpty() ? "" : ", listenAddresses=" + listenAddresses) +
				(sslListenAddresses.isEmpty() ? "" : ", sslListenAddresses=" + sslListenAddresses) +
				(acceptOnce ? ", acceptOnce" : "") +
				'}';
	}
}