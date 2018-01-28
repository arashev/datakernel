package io.datakernel.stream.stats;

import io.datakernel.jmx.JmxAttribute;
import io.datakernel.stream.StreamConsumer;
import io.datakernel.stream.StreamProducer;
import io.datakernel.stream.processor.StreamModifier;
import io.datakernel.util.CollectionUtils;
import io.datakernel.util.IntrusiveLinkedList;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static java.lang.System.currentTimeMillis;

public final class StreamRegistry<V> implements Iterable<V> {
	private final IntrusiveLinkedList<Entry<V>> list = IntrusiveLinkedList.create();
	private int limit = 10;

	public static class Entry<T> {
		private final long timestamp;
		private final T operation;

		private Entry(T operation) {
			this.timestamp = currentTimeMillis();
			this.operation = operation;
		}

		@Override
		public String toString() {
			return operation + " " + (currentTimeMillis() - timestamp);
		}
	}

	public static <V> StreamRegistry<V> create() {
		return new StreamRegistry<>();
	}

	public StreamRegistry<V> withLimit(int limit) {
		this.limit = limit;
		return this;
	}

	@SuppressWarnings("unchecked")
	public <T> StreamModifier<T, T> newEntry(V value) {
		return new StreamModifier<T, T>() {
			@Override
			public StreamConsumer<T> apply(StreamConsumer<T> consumer) {
				return newEntry(consumer, value);
			}

			@Override
			public StreamProducer<T> apply(StreamProducer<T> producer) {
				return newEntry(producer, value);
			}
		};
	}

	public <T> StreamConsumer<T> newEntry(StreamConsumer<T> consumer, V value) {
		Entry<V> entry = new Entry<>(value);
		IntrusiveLinkedList.Node<Entry<V>> node = list.addFirstValue(entry);
		consumer.getEndOfStream().whenComplete(($, throwable) -> list.removeNode(node));
		return consumer;
	}

	public <T> StreamProducer<T> newEntry(StreamProducer<T> producer, V value) {
		Entry<V> entry = new Entry<>(value);
		IntrusiveLinkedList.Node<Entry<V>> node = list.addFirstValue(entry);
		producer.getEndOfStream().whenComplete(($, throwable) -> list.removeNode(node));
		return producer;
	}

	@Override
	public Iterator<V> iterator() {
		Iterator<Entry<V>> iterator = list.iterator();
		return new Iterator<V>() {
			@Override
			public boolean hasNext() {
				return iterator.hasNext();
			}

			@Override
			public V next() {
				return iterator.next().operation;
			}
		};
	}

	@JmxAttribute(name = "")
	public String getString() {
		List<Entry> entries = new ArrayList<>();
		list.forEach(entries::add);
		return CollectionUtils.toLimitedString(entries, limit);
	}

}