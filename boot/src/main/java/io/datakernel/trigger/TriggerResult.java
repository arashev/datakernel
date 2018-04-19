package io.datakernel.trigger;

import io.datakernel.jmx.ExceptionStats;
import io.datakernel.jmx.MBeanFormat;

import java.time.Instant;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static io.datakernel.util.Preconditions.checkState;

public final class TriggerResult {
	private static final TriggerResult NONE = new TriggerResult(0, null, null);

	private final long timestamp;
	private final Throwable throwable;
	private final Object value;
	private final int count;

	TriggerResult(long timestamp, Throwable throwable, Object value, int count) {
		this.timestamp = timestamp;
		this.throwable = throwable;
		this.value = value;
		this.count = count;
	}

	TriggerResult(long timestamp, Throwable throwable, Object value) {
		this(timestamp, throwable, value, 1);
	}

	public static TriggerResult none() {
		return NONE;
	}

	public static TriggerResult create() {
		return new TriggerResult(0L, null, null);
	}

	public static TriggerResult create(long timestamp, Throwable throwable, Object context) {
		return new TriggerResult(timestamp, throwable, context);
	}

	public static TriggerResult create(long timestamp, Throwable throwable, Object context, int count) {
		return new TriggerResult(timestamp, throwable, context, count);
	}

	public static TriggerResult ofTimestamp(long timestamp) {
		return timestamp != 0L ?
				new TriggerResult(0L, null, timestamp) : NONE;
	}

	public static TriggerResult ofTimestamp(long timestamp, Predicate<Long> predicate) {
		return timestamp != 0L && predicate.test(timestamp) ?
				new TriggerResult(0L, null, timestamp) : NONE;
	}

	public static TriggerResult ofTimestamp(long timestamp, boolean condition) {
		return timestamp != 0L && condition ?
				new TriggerResult(0L, null, timestamp) : NONE;
	}

	public static TriggerResult ofError(Throwable throwable) {
		return throwable != null ?
				new TriggerResult(0L, throwable, null) : NONE;
	}

	public static TriggerResult ofError(Throwable throwable, long timestamp) {
		return throwable != null ?
				new TriggerResult(timestamp, throwable, null) : NONE;
	}

	public static TriggerResult ofError(ExceptionStats exceptionStats) {
		Throwable lastException = exceptionStats.getLastException();
		return lastException != null ?
				new TriggerResult(exceptionStats.getLastTimestamp().toEpochMilli(), lastException, exceptionStats.getTotal()) : NONE;
	}

	public static TriggerResult ofValue(Object value) {
		return value != null ?
				new TriggerResult(0L, null, value) : NONE;
	}

	public static <T> TriggerResult ofValue(T value, Predicate<T> predicate) {
		return value != null && predicate.test(value) ?
				new TriggerResult(0L, null, value) : NONE;
	}

	public static <T> TriggerResult ofValue(T value, boolean condition) {
		return value != null && condition ?
				new TriggerResult(0L, null, value) : NONE;
	}

	public static <T> TriggerResult ofValue(Supplier<T> supplier, boolean condition) {
		return condition ? ofValue(supplier.get()) : NONE;
	}

	public TriggerResult withValue(Object value) {
		return isPresent() ? new TriggerResult(timestamp, throwable, value) : NONE;
	}

	public TriggerResult withValue(Supplier<?> value) {
		return isPresent() ? new TriggerResult(timestamp, throwable, value.get()) : NONE;
	}

	public TriggerResult withCount(int count) {
		return isPresent() ? new TriggerResult(timestamp, throwable, value, count) : NONE;
	}

	public TriggerResult withCount(Supplier<Integer> count) {
		return isPresent() ? new TriggerResult(timestamp, throwable, value, count.get()) : NONE;
	}

	public TriggerResult when(boolean condition) {
		return isPresent() && condition ? this : NONE;
	}

	public TriggerResult when(Supplier<Boolean> conditionSupplier) {
		return isPresent() && conditionSupplier.get() ? this : NONE;
	}

	public TriggerResult whenTimestamp(Predicate<Long> timestampPredicate) {
		return isPresent() && timestampPredicate.test(this.timestamp) ? this : NONE;
	}

	@SuppressWarnings("unchecked")
	public <T> TriggerResult whenValue(Predicate<T> valuePredicate) {
		return isPresent() && valuePredicate.test((T) this.value) ? this : NONE;
	}

	public boolean isPresent() {
		return this != NONE;
	}

	public boolean hasTimestamp() {
		return timestamp != 0L;
	}

	public boolean hasThrowable() {
		return throwable != null;
	}

	public boolean hasValue() {
		return value != null;
	}

	public long getTimestamp() {
		checkState(isPresent());
		return timestamp;
	}

	public Throwable getThrowable() {
		checkState(isPresent());
		return throwable;
	}

	public Object getValue() {
		checkState(isPresent());
		return value;
	}

	public int getCount() {
		checkState(isPresent());
		return count;
	}

	@Override
	public String toString() {
		return MBeanFormat.formatTimestamp(Instant.ofEpochMilli(timestamp)) +
				(count != 1 ? " #" + count : "") +
				(value != null ? " : " + value : "") +
				(throwable != null ? "\n" + MBeanFormat.formatExceptionLine(throwable) : "");
	}
}
