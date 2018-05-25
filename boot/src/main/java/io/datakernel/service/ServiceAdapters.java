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

package io.datakernel.service;

import io.datakernel.eventloop.Eventloop;
import io.datakernel.eventloop.EventloopServer;
import io.datakernel.eventloop.EventloopService;
import io.datakernel.net.BlockingSocketServer;
import org.slf4j.Logger;

import javax.sql.DataSource;
import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Static utility methods pertaining to ConcurrentService. Creates
 * ConcurrentService from some other type of instances.
 */
public final class ServiceAdapters {
	private static final Logger logger = getLogger(ServiceAdapters.class);

	private ServiceAdapters() {
	}

	private static BiConsumer<Void, Throwable> completeFuture(CompletableFuture<?> future) {
		return ($, throwable) -> {
			if (throwable != null) {
				future.completeExceptionally(throwable);
			} else {
				future.complete(null);
			}
		};
	}

	public static abstract class SimpleServiceAdapter<S> implements ServiceAdapter<S> {
		private final boolean startConcurrently;
		private final boolean stopConcurrently;

		protected SimpleServiceAdapter(boolean startConcurrently, boolean stopConcurrently) {
			this.startConcurrently = startConcurrently;
			this.stopConcurrently = stopConcurrently;
		}

		protected SimpleServiceAdapter() {
			this(true, true);
		}

		protected abstract void start(S instance) throws Exception;

		protected abstract void stop(S instance) throws Exception;

		@Override
		public final CompletableFuture<Void> start(S instance, Executor executor) {
			CompletableFuture<Void> future = new CompletableFuture<>();
			(startConcurrently ? executor : (Executor) Runnable::run).execute(() -> {
				try {
					start(instance);
					future.complete(null);
				} catch (Exception e) {
					future.completeExceptionally(e);
				}
			});
			return future;
		}

		@Override
		public final CompletableFuture<Void> stop(S instance, Executor executor) {
			CompletableFuture<Void> future = new CompletableFuture<>();
			(stopConcurrently ? executor : (Executor) Runnable::run).execute(() -> {
				try {
					stop(instance);
					future.complete(null);
				} catch (Exception e) {
					future.completeExceptionally(e);
				}
			});
			return future;
		}
	}

	public static ServiceAdapter<Service> forService() {
		return new ServiceAdapter<Service>() {
			@Override
			public CompletableFuture<Void> start(Service instance, Executor executor) {
				return instance.start();
			}

			@Override
			public CompletableFuture<Void> stop(Service instance, Executor executor) {
				return instance.stop();
			}
		};
	}

	public static ServiceAdapter<EventloopService> forEventloopService() {
		return new ServiceAdapter<EventloopService>() {
			@Override
			public CompletableFuture<Void> start(EventloopService instance, Executor executor) {
				CompletableFuture<Void> future = new CompletableFuture<>();
				instance.getEventloop().execute(() -> instance.start().whenComplete(completeFuture(future)));
				return future;
			}

			@Override
			public CompletableFuture<Void> stop(EventloopService instance, Executor executor) {
				CompletableFuture<Void> future = new CompletableFuture<>();
				instance.getEventloop().execute(() -> instance.stop().whenComplete(completeFuture(future)));
				return future;
			}
		};
	}

	public static ServiceAdapter<EventloopServer> forEventloopServer() {
		return new ServiceAdapter<EventloopServer>() {
			@Override
			public CompletableFuture<Void> start(EventloopServer instance, Executor executor) {
				CompletableFuture<Void> future = new CompletableFuture<>();
				instance.getEventloop().execute(() -> {
					try {
						instance.listen();
						future.complete(null);
					} catch (IOException e) {
						future.completeExceptionally(e);
					}
				});
				return future;
			}

			@Override
			public CompletableFuture<Void> stop(EventloopServer instance, Executor executor) {
				CompletableFuture<Void> future = new CompletableFuture<>();
				instance.getEventloop().execute(() -> instance.close().whenComplete(completeFuture(future)));
				return future;
			}
		};
	}

	public static ServiceAdapter<Eventloop> forEventloop(ThreadFactory threadFactory) {
		return new ServiceAdapter<Eventloop>() {
			@Override
			public CompletableFuture<Void> start(Eventloop eventloop, Executor executor) {
				CompletableFuture<Void> future = new CompletableFuture<>();
				threadFactory.newThread(() -> {
					eventloop.keepAlive(true);
					future.complete(null);
					eventloop.run();
				}).start();
				return future;
			}

			@Override
			public CompletableFuture<Void> stop(Eventloop eventloop, Executor executor) {
				CompletableFuture<Void> future = new CompletableFuture<>();
				Thread eventloopThread = eventloop.getEventloopThread();
				assert eventloopThread != null;
				eventloop.execute(() -> eventloop.keepAlive(false));
				executor.execute(() -> {
					try {
						eventloopThread.join();
						future.complete(null);
					} catch (InterruptedException e) {
						future.completeExceptionally(e);
					}
				});
				return future;
			}
		};
	}

	public static ServiceAdapter<Eventloop> forEventloop() {
		return forEventloop(r -> {
			Thread thread = Executors.defaultThreadFactory().newThread(r);
			thread.setName("eventloop: " + thread.getName());
			return thread;
		});
	}

	/**
	 * Returns factory which transforms blocking Service to asynchronous non-blocking ConcurrentService. It runs blocking operations from other thread from executor.
	 */
	public static ServiceAdapter<BlockingService> forBlockingService() {
		return new SimpleServiceAdapter<BlockingService>() {
			@Override
			protected void start(BlockingService instance) throws Exception {
				instance.start();
			}

			@Override
			protected void stop(BlockingService instance) throws Exception {
				instance.stop();
			}
		};
	}

	public static ServiceAdapter<BlockingSocketServer> forBlockingSocketServer() {
		return new SimpleServiceAdapter<BlockingSocketServer>() {
			@Override
			protected void start(BlockingSocketServer instance) throws Exception {
				instance.start();
			}

			@Override
			protected void stop(BlockingSocketServer instance) throws Exception {
				instance.stop();
			}
		};
	}

	/**
	 * Returns factory which transforms Timer to ConcurrentService. On starting it doing nothing, on stop it cancel timer.
	 */
	public static ServiceAdapter<Timer> forTimer() {
		return new SimpleServiceAdapter<Timer>(false, false) {
			@Override
			protected void start(Timer instance) throws Exception {
			}

			@Override
			protected void stop(Timer instance) throws Exception {
				instance.cancel();
			}
		};
	}

	/**
	 * Returns factory which transforms ExecutorService to ConcurrentService. On starting it doing nothing, on stopping it shuts down ExecutorService.
	 */
	public static ServiceAdapter<ExecutorService> forExecutorService() {
		return new SimpleServiceAdapter<ExecutorService>(false, true) {
			@Override
			protected void start(ExecutorService instance) throws Exception {
			}

			@Override
			protected void stop(ExecutorService instance) throws Exception {
				List<Runnable> runnables = instance.shutdownNow();
				if (!runnables.isEmpty()) {
					logger.warn("Cancelled tasks: " + runnables);
				}
				if (!instance.isTerminated()) {
					logger.warn("Awaiting termination of " + instance + " ...");
					instance.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
				}
			}
		};
	}

	/**
	 * Returns factory which transforms Closeable object to ConcurrentService. On starting it doing nothing, on stopping it close Closeable.
	 */
	public static ServiceAdapter<Closeable> forCloseable() {
		return new SimpleServiceAdapter<Closeable>(false, true) {
			@Override
			protected void start(Closeable instance) throws Exception {
			}

			@Override
			protected void stop(Closeable instance) throws Exception {
				instance.close();
			}
		};
	}

	/**
	 * Returns factory which transforms DataSource object to ConcurrentService. On starting it checks connecting , on stopping it close DataSource.
	 */
	public static ServiceAdapter<DataSource> forDataSource() {
		return new SimpleServiceAdapter<DataSource>(true, false) {
			@Override
			protected void start(DataSource instance) throws Exception {
				Connection connection = instance.getConnection();
				connection.close();
			}

			@Override
			protected void stop(DataSource instance) throws Exception {
			}
		};
	}

	public static <T> ServiceAdapter<T> immediateServiceAdapter() {
		return new SimpleServiceAdapter<T>(false, false) {
			@Override
			protected void start(T instance) throws Exception {
			}

			@Override
			protected void stop(T instance) throws Exception {
			}
		};
	}

	@SafeVarargs
	public static <T> ServiceAdapter<T> combinedAdapter(ServiceAdapter<? super T>... startOrder) {
		return combinedAdapter(Arrays.asList(startOrder));
	}

	public static <T> ServiceAdapter<T> combinedAdapter(List<? extends ServiceAdapter<? super T>> startOrder) {
		List<? extends ServiceAdapter<? super T>> stopOrder = new ArrayList<>(startOrder);
		Collections.reverse(stopOrder);
		return combinedAdapter(startOrder, stopOrder);
	}

	@FunctionalInterface
	private interface Action<T> {
		CompletableFuture<Void> doAction(ServiceAdapter<T> serviceAdapter, T instance, Executor executor);
	}

	public static <T> ServiceAdapter<T> combinedAdapter(List<? extends ServiceAdapter<? super T>> startOrder,
														List<? extends ServiceAdapter<? super T>> stopOrder) {
		return new ServiceAdapter<T>() {
			@SuppressWarnings("unchecked")
			private void doAction(T instance, Executor executor,
								  Iterator<? extends ServiceAdapter<? super T>> iterator, CompletableFuture<Void> future,
								  Action<T> action) {
				if (iterator.hasNext()) {
					action.doAction((ServiceAdapter<T>) iterator.next(), instance, executor).whenCompleteAsync((o, throwable) -> {
						if (throwable == null) {
							doAction(instance, executor, iterator, future, action);
						} else if (throwable instanceof InterruptedException) {
							future.completeExceptionally(throwable);
						} else if (throwable instanceof ExecutionException) {
							future.completeExceptionally(throwable.getCause());
						}
					}, Runnable::run);
				} else {
					future.complete(null);
				}
			}

			@Override
			public CompletableFuture<Void> start(T instance, Executor executor) {
				CompletableFuture<Void> future = new CompletableFuture<>();
				doAction(instance, executor, startOrder.iterator(), future,
					ServiceAdapter::start);
				return future;
			}

			@Override
			public CompletableFuture<Void> stop(T instance, Executor executor) {
				CompletableFuture<Void> future = new CompletableFuture<>();
				doAction(instance, executor, stopOrder.iterator(), future,
					ServiceAdapter::stop);
				return future;
			}
		};
	}
}
