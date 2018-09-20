package io.datakernel.http.stream2;

import io.datakernel.async.AsyncProcess;
import io.datakernel.async.Stages;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.bytebuf.ByteBufPool;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.eventloop.FatalErrorHandlers;
import io.datakernel.http.TestUtils.AssertingConsumer;
import io.datakernel.stream.processor.ByteBufRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Random;

import static io.datakernel.serial.SerialSupplier.ofIterable;
import static io.datakernel.test.TestUtils.assertComplete;
import static org.junit.Assert.assertTrue;

public class BufsConsumerIntegrationTest {
	@Rule
	public ByteBufRule byteBufRule = new ByteBufRule();
	public final AssertingConsumer consumer = new AssertingConsumer();
	public final ArrayList<ByteBuf> list = new ArrayList<>();
	public final Random random = new Random();
	public BufsConsumerChunkedEncoder chunkedEncoder = BufsConsumerChunkedEncoder.create();
	public BufsConsumerChunkedDecoder chunkedDecoder = BufsConsumerChunkedDecoder.create();
	public BufsConsumerGzipDeflater gzipDeflater = BufsConsumerGzipDeflater.create();
	public BufsConsumerGzipInflater gzipInflater = BufsConsumerGzipInflater.create();
	public final Eventloop eventloop = Eventloop.create().withCurrentThread().withFatalErrorHandler(FatalErrorHandlers.rethrowOnAnyError());

	@Before
	public void setUp() {
		consumer.reset();
	}

	@Test
	public void testEncodeDecodeSingleBuf() {
		chunkedEncoder.setOutput(chunkedDecoder.getInputConsumer());
		chunkedDecoder.setOutput(consumer);
		writeSingleBuf();
		chunkedEncoder.setInput(ofIterable(list));
		doTest(chunkedEncoder, chunkedDecoder);
	}

	@Test
	public void testEncodeDecodeMultipleBufs() {
		chunkedEncoder.setOutput(chunkedDecoder.getInputConsumer());
		chunkedDecoder.setOutput(consumer);
		writeMultipleBufs();
		chunkedEncoder.setInput(ofIterable(list));
		doTest(chunkedEncoder, chunkedDecoder);
	}

	@Test
	public void testGzipGunzipSingleBuf() {
		gzipDeflater.setOutput(gzipInflater.getInputConsumer());
		gzipInflater.setOutput(consumer);
		writeSingleBuf();
		gzipDeflater.setInput(ofIterable(list));
		doTest(gzipInflater, gzipDeflater);
	}

	@Test
	public void testGzipGunzipMultipleBufs() {
		gzipDeflater.setOutput(gzipInflater.getInputConsumer());
		gzipInflater.setOutput(consumer);
		writeMultipleBufs();
		gzipDeflater.setInput(ofIterable(list));
		doTest(gzipInflater, gzipDeflater);
	}

	private void writeSingleBuf() {
		byte[] data = new byte[1000];
		random.nextBytes(data);
		consumer.setExpectedByteArray(data);
		ByteBuf buf = ByteBufPool.allocate(data.length);
		buf.put(data);
		list.add(buf);
	}

	private void writeMultipleBufs() {
		byte[] data = new byte[100_000];
		random.nextBytes(data);
		ByteBuf toBeSplitted = ByteBufPool.allocate(data.length);
		ByteBuf expected = ByteBufPool.allocate(data.length);
		toBeSplitted.put(data);
		expected.put(data);
		consumer.setExpectedBuf(expected);
		while (toBeSplitted.isRecycleNeeded() && toBeSplitted.readRemaining() != 0) {
			int part = Math.min(random.nextInt(100) + 100, toBeSplitted.readRemaining());
			ByteBuf slice = toBeSplitted.slice(part);
			toBeSplitted.moveReadPosition(part);
			list.add(slice);
		}
		toBeSplitted.recycle();
	}

	private void doTest(AsyncProcess process1, AsyncProcess process2) {
		eventloop.post(() -> Stages.all(process1.start(), process2.start())
				.whenComplete(assertComplete())
				.whenComplete(($, e) -> assertTrue(consumer.executed)));

		eventloop.run();
	}
}