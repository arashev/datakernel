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

package io.datakernel.logfs.ot;

import io.datakernel.async.StagesAccumulator;
import io.datakernel.stream.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static io.datakernel.stream.DataStreams.stream;
import static io.datakernel.util.Preconditions.checkState;

@SuppressWarnings("unchecked")
public abstract class LogDataConsumerSplitter<T, D> implements LogDataConsumer<T, D> {
	private final List<LogDataConsumer<?, D>> logDataConsumers = new ArrayList<>();
	private Iterator<? extends StreamDataReceiver<?>> receivers;

	@Override
	public StreamConsumerWithResult<T, List<D>> consume() {
		if (logDataConsumers.isEmpty()) {
			createSplitter(); // recording scheme
			checkState(!logDataConsumers.isEmpty(), "addOutput() should be called at least once");
		}
		StagesAccumulator<List<D>> resultsReducer = StagesAccumulator.create(new ArrayList<>());
		Splitter splitter = new Splitter();
		for (LogDataConsumer<?, D> logDataConsumer : logDataConsumers) {
			StreamConsumerWithResult<?, List<D>> consumer = logDataConsumer.consume();
			resultsReducer.addStage(consumer.getResult(), List::addAll);
			Splitter.Output<?> output = splitter.new Output<>();
			splitter.outputs.add(output);
			stream(output, (StreamConsumer) consumer);
		}
		return splitter.getInput().withResult(resultsReducer.get());
	}

	protected abstract StreamDataReceiver<T> createSplitter();

	protected final <X> StreamDataReceiver<X> addOutput(LogDataConsumer<X, D> logDataConsumer) {
		if (receivers == null) {
			// initial run, recording scheme
			logDataConsumers.add(logDataConsumer);
			return null;
		}
		// receivers must correspond outputs for recorded scheme
		return (StreamDataReceiver<X>) receivers.next();
	}

	final class Splitter implements HasInput<T>, HasOutputs {
		private final Input input;
		private final List<Output<?>> outputs = new ArrayList<>();

		private StreamDataReceiver<T> inputReceiver;

		private int ready = 0;

		protected Splitter() {
			this.input = new Input();
		}

		@Override
		public StreamConsumer<T> getInput() {
			return input;
		}

		@Override
		public List<? extends StreamProducer<?>> getOutputs() {
			return outputs;
		}

		final class Input extends AbstractStreamConsumer<T> {
			@Override
			protected void onEndOfStream() {
				outputs.forEach(Output::sendEndOfStream);
			}

			@Override
			protected void onError(Throwable t) {
				outputs.forEach(output -> output.closeWithError(t));
			}
		}

		final class Output<X> extends AbstractStreamProducer<X> {
			private StreamDataReceiver<?> dataReceiver;

			@Override
			protected void onProduce(StreamDataReceiver<X> dataReceiver) {
				this.dataReceiver = dataReceiver;
				if (++ready == outputs.size()) {
					if (inputReceiver == null) {
						receivers = outputs.stream().map(output -> output.dataReceiver).iterator();
						inputReceiver = createSplitter();
						checkState(!receivers.hasNext());
						receivers = null;
					}
					input.getProducer().produce(inputReceiver);
				}
			}

			@Override
			protected void onSuspended() {
				--ready;
				input.getProducer().suspend();
			}

			@Override
			protected void onError(Throwable t) {
				input.closeWithError(t);
			}
		}
	}
}
