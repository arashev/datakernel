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

package io.datakernel.eventloop;

import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.bytebuf.ByteBufPool;
import io.datakernel.jmx.EventStats;
import io.datakernel.jmx.JmxAttribute;
import io.datakernel.jmx.ValueStats;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayDeque;

import static io.datakernel.eventloop.AsyncTcpSocketImpl.OP_POSTPONED;
import static io.datakernel.util.Preconditions.checkNotNull;

public final class AsyncUdpSocketImpl implements AsyncUdpSocket, NioChannelEventHandler {
	private static final int DEFAULT_UDP_BUFFER_SIZE = 16 * 1024;

	private final Eventloop eventloop;
	private SelectionKey key;

	private int receiveBufferSize = DEFAULT_UDP_BUFFER_SIZE;

	private final DatagramChannel channel;
	private final ArrayDeque<UdpPacket> writeQueue = new ArrayDeque<>();

	private AsyncUdpSocket.EventHandler eventHandler;

	private int ops = 0;

	// region jmx classes
	public interface Inspector {
		void onReceive(UdpPacket packet);

		void onReceiveError(IOException e);

		void onSend(UdpPacket packet);

		void onSendError(IOException e);
	}

	public static class JmxInspector implements Inspector {
		private final ValueStats receives;
		private final EventStats receiveErrors;
		private final ValueStats sends;
		private final EventStats sendErrors;

		public JmxInspector(double smoothingWindow) {
			this.receives = ValueStats.create(smoothingWindow);
			this.receiveErrors = EventStats.create(smoothingWindow);
			this.sends = ValueStats.create(smoothingWindow);
			this.sendErrors = EventStats.create(smoothingWindow);
		}

		@Override
		public void onReceive(UdpPacket packet) {
			receives.recordValue(packet.getBuf().readRemaining());
		}

		@Override
		public void onReceiveError(IOException e) {
			receiveErrors.recordEvent();
		}

		@Override
		public void onSend(UdpPacket packet) {
			sends.recordValue(packet.getBuf().readRemaining());
		}

		@Override
		public void onSendError(IOException e) {
			sendErrors.recordEvent();
		}

		@JmxAttribute(description = "Received packet size")
		public ValueStats getReceives() {
			return receives;
		}

		@JmxAttribute
		public EventStats getReceiveErrors() {
			return receiveErrors;
		}

		@JmxAttribute(description = "Sent packet size")
		public ValueStats getSends() {
			return sends;
		}

		@JmxAttribute
		public EventStats getSendErrors() {
			return sendErrors;
		}
	}
	// endregion

	private Inspector inspector;

	// region creators && builder methods
	private AsyncUdpSocketImpl(Eventloop eventloop, DatagramChannel channel) {
		this.eventloop = checkNotNull(eventloop);
		this.channel = checkNotNull(channel);
	}

	public static AsyncUdpSocketImpl create(Eventloop eventloop, DatagramChannel channel) {
		return new AsyncUdpSocketImpl(eventloop, channel);
	}

	public AsyncUdpSocketImpl withInspector(Inspector inspector) {
		this.inspector = inspector;
		return this;
	}
	// endregion

	@Override
	public void setEventHandler(AsyncUdpSocket.EventHandler eventHandler) {
		this.eventHandler = eventHandler;
	}

	public void setReceiveBufferSize(int receiveBufferSize) {
		this.receiveBufferSize = receiveBufferSize;
	}

	//  miscellaneous
	public void register() {
		try {
			key = channel.register(eventloop.ensureSelector(), ops, this);
		} catch (final IOException e) {
			eventloop.post(new Runnable() {
				@Override
				public void run() {
					closeChannel();
					eventHandler.onClosedWithError(e);
				}
			});
		}
		eventHandler.onRegistered();
	}

	public final boolean isOpen() {
		return key != null;
	}

	//  read cycle
	@Override
	public void receive() {
		readInterest(true);
	}

	@Override
	public void onReadReady() {
		while (isOpen()) {
			ByteBuf buf = ByteBufPool.allocate(receiveBufferSize);
			ByteBuffer buffer = buf.toWriteByteBuffer();
			InetSocketAddress sourceAddress;
			try {
				sourceAddress = (InetSocketAddress) channel.receive(buffer);
			} catch (IOException e) {
				buf.recycle();
				if (inspector != null) inspector.onReceiveError(e);
				closeWithError(e);
				return;
			}

			if (sourceAddress == null) {
				buf.recycle();
				break;
			}

			buf.ofWriteByteBuffer(buffer);
			UdpPacket packet = UdpPacket.of(buf, sourceAddress);
			if (inspector != null) inspector.onReceive(packet);
			eventHandler.onReceive(packet);
		}
	}

	//  write cycle
	@Override
	public void send(UdpPacket packet) {
		writeQueue.add(packet);
		onWriteReady();
	}

	@Override
	public void onWriteReady() {
		while (!writeQueue.isEmpty()) {
			UdpPacket packet = writeQueue.peek();
			ByteBuffer buffer = packet.getBuf().toReadByteBuffer();

			int needToSend = buffer.remaining();
			int sent;

			try {
				sent = channel.send(buffer, packet.getSocketAddress());
			} catch (IOException e) {
				if (inspector != null) inspector.onSendError(e);
				closeWithError(e);
				return;
			}

			if (sent != needToSend) {
				break;
			}

			if (inspector != null) inspector.onSend(packet);

			writeQueue.poll();
			packet.recycle();
		}

		if (writeQueue.isEmpty()) {
			eventHandler.onSend();
			writeInterest(false);
		} else {
			writeInterest(true);
		}
	}

	// interests management
	@SuppressWarnings("MagicConstant")
	private void interests(int newOps) {
		if (ops != newOps) {
			ops = newOps;
			if ((ops & OP_POSTPONED) == 0 && key != null) {
				key.interestOps(ops);
			}
		}
	}

	private void readInterest(boolean readInterest) {
		interests(readInterest ? (ops | SelectionKey.OP_READ) : (ops & ~SelectionKey.OP_READ));
	}

	private void writeInterest(boolean writeInterest) {
		interests(writeInterest ? (ops | SelectionKey.OP_WRITE) : (ops & ~SelectionKey.OP_WRITE));
	}

	//  close handling
	@Override
	public void close() {
		assert eventloop.inEventloopThread();
		if (key == null) return;
		closeChannel();
		key = null;
		for (UdpPacket packet : writeQueue) {
			packet.recycle();
		}
		writeQueue.clear();
	}

	private void closeChannel() {
		if (channel == null) return;
		try {
			channel.close();
		} catch (IOException e) {
		}
	}

	private void closeWithError(final IOException e) {
		if (isOpen()) {
			close();
			eventHandler.onClosedWithError(e);
		}
	}

	@Override
	public String toString() {
		return getRemoteSocketAddress() + " " + eventHandler.toString();
	}

	private InetSocketAddress getRemoteSocketAddress() {
		try {
			return (InetSocketAddress) channel.getRemoteAddress();
		} catch (IOException ignored) {
			throw new AssertionError("I/O error occurs or channel closed");
		}
	}
}
