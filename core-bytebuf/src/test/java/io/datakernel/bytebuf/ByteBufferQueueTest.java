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

package io.datakernel.bytebuf;

import org.junit.Test;

import java.util.Random;

import static io.datakernel.bytebuf.ByteBufTest.initByteBufPool;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ByteBufferQueueTest {
	static {
		initByteBufPool();
	}

	private final Random random = new Random();

	@Test
	public void test() {
		byte[] test = new byte[200];
		for (int i = 0; i < test.length; i++) {
			test[i] = (byte) (i + 1);
		}

		ByteBufQueue queue = new ByteBufQueue();

		int left = test.length;
		int pos = 0;
		while (left > 0) {
			int bufSize = random.nextInt(Math.min(10, left) + 1);
			ByteBuf buf = ByteBuf.wrap(test, pos, pos + bufSize);
			queue.add(buf);
			left -= bufSize;
			pos += bufSize;
		}

		assertEquals(test.length, queue.remainingBytes());

		left = test.length;
		pos = 0;
		while (left > 0) {
			int requested = random.nextInt(50);
			byte[] dest = new byte[100];
			int drained = queue.drainTo(dest, 10, requested);

			assertTrue(drained <= requested);

			for (int i = 0; i < drained; i++) {
				assertEquals(test[i + pos], dest[i + 10]);
			}

			left -= drained;
			pos += drained;
		}

		assertEquals(0, queue.remainingBytes());
	}

}
