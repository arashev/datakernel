package io.datakernel.serial;

import io.datakernel.async.MaterializedStage;
import io.datakernel.async.Stage;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public interface SerialInput<T> {
	MaterializedStage<Void> set(SerialSupplier<T> input);

	default SerialConsumer<T> getConsumer() {
		return getConsumer(new SerialZeroBuffer<>());
	}

	default SerialConsumer<T> getConsumer(SerialQueue<T> queue) {
		MaterializedStage<Void> extraAcknowledge = set(queue.getSupplier());
		return queue.getConsumer(extraAcknowledge);
	}

	default <R> SerialInput<R> apply(SerialSupplierFunction<R, SerialSupplier<T>> fn) {
		return input -> SerialInput.this.set(fn.apply(input));
	}

	default <R> SerialInput<R> transform(Function<? super R, ? extends T> fn) {
		return input -> SerialInput.this.set(input.transform(fn));
	}

	default <R> SerialInput<R> transformAsync(Function<? super R, ? extends Stage<T>> fn) {
		return input -> SerialInput.this.set(input.transformAsync(fn));
	}

	default SerialInput<T> filter(Predicate<? super T> predicate) {
		return input -> SerialInput.this.set(input.filter(predicate));
	}

	default SerialInput<T> peek(Consumer<? super T> peek) {
		return input -> SerialInput.this.set(input.peek(peek));
	}

}