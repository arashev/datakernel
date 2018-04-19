package io.datakernel.jmx;

import io.datakernel.async.AsyncCallable;
import io.datakernel.async.Stage;
import io.datakernel.async.StageConsumer;
import io.datakernel.eventloop.Eventloop;

import java.time.Duration;
import java.time.Instant;

import static io.datakernel.eventloop.Eventloop.getCurrentEventloop;
import static io.datakernel.jmx.JmxReducers.JmxReducerMax;
import static io.datakernel.jmx.JmxReducers.JmxReducerSum;

public class StageStats {
	private Eventloop eventloop;

	private int activeStages = 0;
	private Instant lastStartTimestamp = Instant.EPOCH;
	private Instant lastCompleteTimestamp = Instant.EPOCH;
	private final ValueStats duration;
	private final ExceptionStats exceptions = ExceptionStats.create();

	StageStats(Eventloop eventloop, ValueStats duration) {
		this.eventloop = eventloop;
		this.duration = duration;
	}

	public static StageStats createMBean(Eventloop eventloop, Duration smoothingWindowSeconds) {
		return new StageStats(eventloop, ValueStats.create(smoothingWindowSeconds));
	}

	public static StageStats create(Duration smoothingWindow) {
		return new StageStats(null, ValueStats.create(smoothingWindow));
	}

	public StageStats withHistogram(int[] levels) {
		setHistogramLevels(levels);
		return this;
	}

	public void setHistogramLevels(int[] levels) {
		duration.setHistogramLevels(levels);
	}

	private long currentTimeMillis() {
		if (eventloop == null) {
			eventloop = getCurrentEventloop();
		}
		return eventloop.currentTimeMillis();
	}

	public <T> AsyncCallable<T> wrapper(AsyncCallable<T> callable) {
		return () -> monitor(callable.call());
	}

	public <T> Stage<T> monitor(Stage<T> stage) {
		return stage.whenComplete(recordStats());
	}

	public <T> StageConsumer<T> recordStats() {
		this.activeStages++;
		long before = currentTimeMillis();
		this.lastStartTimestamp = Instant.ofEpochMilli(before);
		return (value, throwable) -> {
			this.activeStages--;
			long now = currentTimeMillis();
			long durationMillis = now - before;
			this.lastCompleteTimestamp = Instant.ofEpochMilli(now);
			duration.recordValue(durationMillis);

			if (throwable != null) {
				exceptions.recordException(throwable);
			}
		};
	}

	@JmxAttribute(reducer = JmxReducerSum.class)
	public long getActiveStages() {
		return activeStages;
	}

	@JmxAttribute(reducer = JmxReducerMax.class, optional = true)
	public Instant getLastStartTimestamp() {
		return lastStartTimestamp;
	}

	@JmxAttribute
	public Instant getLastStartTime() {
		return lastStartTimestamp;
	}

	@JmxAttribute(reducer = JmxReducerMax.class, optional = true)
	public Instant getLastCompleteTimestamp() {
		return lastCompleteTimestamp;
	}

	@JmxAttribute
	public Instant getLastCompleteTime() {
		return lastCompleteTimestamp;
	}

	@JmxAttribute(reducer = JmxReducerMax.class)
	public Duration getCurrentDuration() {
		return activeStages != 0 ? Duration.ofMillis(currentTimeMillis()).minusMillis(lastStartTimestamp.toEpochMilli()) : Duration.ZERO;
	}

	@JmxAttribute
	public ValueStats getDuration() {
		return duration;
	}

	@JmxAttribute
	public ExceptionStats getExceptions() {
		return exceptions;
	}
}