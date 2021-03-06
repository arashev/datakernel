/*
 * Copyright (C) 2015-2018 SoftIndex LLC.
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

package io.datakernel.datastream;

import io.datakernel.async.process.Cancellable;
import io.datakernel.csp.AbstractChannelConsumer;
import io.datakernel.csp.ChannelConsumer;
import io.datakernel.datastream.StreamConsumers.ClosingWithErrorImpl;
import io.datakernel.datastream.StreamConsumers.Idle;
import io.datakernel.datastream.StreamConsumers.OfChannelConsumerImpl;
import io.datakernel.datastream.StreamConsumers.Skip;
import io.datakernel.datastream.processor.StreamLateBinder;
import io.datakernel.datastream.processor.StreamTransformer;
import io.datakernel.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.datakernel.common.Preconditions.checkArgument;
import static io.datakernel.datastream.StreamCapability.LATE_BINDING;

/**
 * It represents an object which can asynchronous receive streams of data.
 * Implementors of this interface are strongly encouraged to extend one of the abstract classes
 * in this package which implement this interface and make the threading and state management
 * easier.
 */
public interface StreamConsumer<T> extends Cancellable {
	/**
	 * Sets wired supplier. It will sent data to this consumer
	 *
	 * @param supplier stream supplier for setting
	 */
	void setSupplier(@NotNull StreamSupplier<T> supplier);

	Promise<Void> getAcknowledgement();

	Set<StreamCapability> getCapabilities();

	static <T> StreamConsumer<T> idle() {
		return new Idle<>();
	}

	static <T> StreamConsumer<T> skip() {
		return new Skip<>();
	}

	/**
	 * @deprecated use of this consumer is discouraged as it breaks the whole asynchronous model.
	 * Exists only for testing
	 */
	@Deprecated
	static <T> StreamConsumer<T> of(Consumer<T> consumer) {
		return new StreamConsumers.OfConsumerImpl<>(consumer);
	}

	static <T> StreamConsumer<T> closingWithError(Throwable e) {
		return new ClosingWithErrorImpl<>(e);
	}

	static <T> StreamConsumer<T> ofChannelConsumer(ChannelConsumer<T> consumer) {
		return new OfChannelConsumerImpl<>(consumer);
	}

	static <T> StreamConsumer<T> ofSupplier(Function<StreamSupplier<T>, Promise<Void>> supplier) {
		StreamTransformer<T, T> forwarder = StreamTransformer.identity();
		Promise<Void> extraAcknowledge = supplier.apply(forwarder.getOutput());
		StreamConsumer<T> result = forwarder.getInput();
		if (extraAcknowledge == Promise.complete()) return result;
		return result
				.withAcknowledgement(ack -> ack.both(extraAcknowledge));
	}

	default <R> R transformWith(StreamConsumerTransformer<T, R> fn) {
		return fn.transform(this);
	}

	default StreamConsumer<T> withLateBinding() {
		return getCapabilities().contains(LATE_BINDING) ? this : transformWith(StreamLateBinder.create());
	}

	default ChannelConsumer<T> asSerialConsumer() {
		StreamSupplierEndpoint<T> endpoint = new StreamSupplierEndpoint<>();
		endpoint.streamTo(this);
		return new AbstractChannelConsumer<T>(this) {
			@Override
			protected Promise<Void> doAccept(T item) {
				if (item != null) return endpoint.put(item);
				return endpoint.put(null).both(endpoint.getConsumer().getAcknowledgement());
			}
		};
	}

	String LATE_BINDING_ERROR_MESSAGE = "" +
			"StreamConsumer %s does not have LATE_BINDING capabilities, " +
			"it must be bound in the same tick when it is created. " +
			"Alternatively, use .withLateBinding() modifier";

	static <T> StreamConsumer<T> ofPromise(Promise<? extends StreamConsumer<T>> promise) {
		if (promise.isResult()) return promise.getResult();
		StreamLateBinder<T> lateBounder = StreamLateBinder.create();
		promise.whenComplete((consumer, e) -> {
			if (e == null) {
				checkArgument(consumer.getCapabilities().contains(LATE_BINDING),
						LATE_BINDING_ERROR_MESSAGE, consumer);
				lateBounder.getOutput().streamTo(consumer);
			} else {
				lateBounder.getOutput().streamTo(closingWithError(e));
			}
		});
		return lateBounder.getInput();
	}

	default StreamConsumer<T> withAcknowledgement(Function<Promise<Void>, Promise<Void>> fn) {
		Promise<Void> acknowledgement = getAcknowledgement();
		Promise<Void> newAcknowledgement = fn.apply(acknowledgement);
		if (acknowledgement == newAcknowledgement) return this;
		return new ForwardingStreamConsumer<T>(this) {
			@Override
			public Promise<Void> getAcknowledgement() {
				return newAcknowledgement;
			}
		};
	}

}
