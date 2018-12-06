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

package io.datakernel.csp.process;

import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.bytebuf.ByteBufQueue;
import io.datakernel.csp.ChannelInput;
import io.datakernel.csp.ChannelSupplier;
import io.datakernel.exception.InvalidSizeException;
import io.datakernel.exception.ParseException;
import io.datakernel.exception.TruncatedDataException;
import io.datakernel.serializer.BinarySerializer;
import io.datakernel.stream.AbstractStreamSupplier;

import static java.lang.String.format;

/**
 * Represent deserializer which deserializes data from ByteBuffer to some type. Is a {@link AbstractStreamTransformer}
 * which receives ByteBufs and streams specified type.
 *
 * @param <T> original type of data
 */
public final class ChannelDeserializer<T> extends AbstractStreamSupplier<T> implements WithChannelToStream<ChannelDeserializer<T>, ByteBuf, T> {
	public static final ParseException HEADER_SIZE_EXCEPTION = new ParseException(ChannelDeserializer.class, "Header size is too large");
	public static final ParseException DESERIALIZED_SIZE_EXCEPTION = new InvalidSizeException(ChannelDeserializer.class, "Deserialized size != parsed data size");

	private ChannelSupplier<ByteBuf> input;
	private final BinarySerializer<T> valueSerializer;

	private final ByteBufQueue queue = new ByteBufQueue();

	// region creators
	private ChannelDeserializer(BinarySerializer<T> valueSerializer) {
		this.valueSerializer = valueSerializer;
	}

	public static <T> ChannelDeserializer<T> create(BinarySerializer<T> valueSerializer) {
		return new ChannelDeserializer<>(valueSerializer);
	}

	@Override
	public ChannelInput<ByteBuf> getInput() {
		return input -> {
			this.input = input;
			return getAcknowledgement();
		};
	}
	// endregion

	@Override
	protected void produce(AsyncProduceController async) {
		async.begin();
		try {
			while (isReceiverReady() && queue.hasRemaining()) {
				int dataSize = tryPeekSize(queue);
				int headerSize = dataSize >>> 24;
				int size = headerSize + (dataSize & 0xFFFFFF);

				if (headerSize == 0)
					break;

				if (!queue.hasRemainingBytes(size))
					break;

				ByteBuf buf = queue.takeExactSize(size);
				buf.moveReadPosition(headerSize);

				T item;
				try {
					item = valueSerializer.decode(buf.array(), buf.readPosition());
				} catch (Exception e) {
					throw new ParseException(ChannelDeserializer.class, "Deserialization error", e);
				}

				buf.recycle();

				send(item);
			}

			if (isReceiverReady()) {
				input.get()
						.whenResult(buf -> {
							if (buf != null) {
								queue.add(buf);
								async.resume();
							} else {
								if (queue.isEmpty()) {
									sendEndOfStream();
								} else {
									close(new TruncatedDataException(ChannelDeserializer.class, format("Truncated serialized data stream, %s : %s", this, queue)));
								}
							}
						})
						.whenException(this::close);
			} else {
				async.end();
			}
		} catch (ParseException e) {
			close(e);
		}
	}

	@Override
	protected void onError(Throwable e) {
		queue.recycle();
		input.close(e);
	}

	private static int tryPeekSize(ByteBufQueue queue) throws ParseException {
		assert queue.hasRemaining();
		int dataSize = 0;
		int headerSize = 0;
		byte b = queue.peekByte();
		if (b >= 0) {
			dataSize = b;
			headerSize = 1;
		} else if (queue.hasRemainingBytes(2)) {
			dataSize = b & 0x7f;
			b = queue.peekByte(1);
			if (b >= 0) {
				dataSize |= (b << 7);
				headerSize = 2;
			} else if (queue.hasRemainingBytes(3)) {
				dataSize |= ((b & 0x7f) << 7);
				b = queue.peekByte(2);
				if (b >= 0) {
					dataSize |= (b << 14);
					headerSize = 3;
				} else
					throw HEADER_SIZE_EXCEPTION;
			}
		}
		return (headerSize << 24) + dataSize;
	}

}