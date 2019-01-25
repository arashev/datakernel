/*
 * Copyright (C) 2015-2019 SoftIndex LLC.
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

package io.datakernel.crdt;

import io.datakernel.async.Promise;
import io.datakernel.stream.StreamConsumer;
import io.datakernel.stream.StreamSupplier;
import io.datakernel.stream.StreamSupplierWithResult;

/**
 * Interface for various CRDT client implementations.
 * CRDT storage can be seen as SortedMap where put operation is same as merge with CRDT union as combiner.
 *
 * @param <K> type of crdt keys
 * @param <S> type of crdt states
 */
public interface CrdtClient<K extends Comparable<K>, S> {

	/**
	 * Returns a consumer of key-state pairs to be added to the CRDT storage.
	 *
	 * @return stage of stream consumer of key-state pairs.
	 */
	Promise<StreamConsumer<CrdtData<K, S>>> upload();

	/**
	 * Returns a producer if all key-state pairs in the CRDT storage that were put AFTER given token was received.
	 * Pairs are sorted by key.
	 *
	 * @return stage of stream producer of key-state pairs
	 */
	Promise<StreamSupplierWithResult<CrdtData<K, S>, Long>> download(long token);

	/**
	 * Same as above, but downloads all possible key-state pairs.
	 *
	 * @return stage of stream producer of key-state pairs
	 */
	default Promise<StreamSupplierWithResult<CrdtData<K, S>, Long>> download() {
		return download(0);
	}

	/**
	 * Returns a consumer of keys to be removed from the CRDT storage.
	 * Actual removal could be implemented differently, by actually deleting, or e.g. by timestamp tombstones.
	 *
	 * @return stage of stream consumer of keys
	 */
	Promise<StreamConsumer<K>> remove();

	/**
	 * Marker that this client is functional (server is up, there are enough nodes in cluster etc.)
	 *
	 * @return stage that succeeds if this client is up
	 */
	Promise<Void> ping();

	class CrdtStreamSupplierWithToken<K extends Comparable<K>, S> {
		private final Promise<StreamSupplier<CrdtData<K, S>>> streamPromise;
		private final Promise<Long> tokenPromise;

		public CrdtStreamSupplierWithToken(Promise<StreamSupplier<CrdtData<K, S>>> streamPromise, Promise<Long> tokenPromise) {
			this.streamPromise = streamPromise;
			this.tokenPromise = tokenPromise;
		}

		public Promise<StreamSupplier<CrdtData<K, S>>> getStreamPromise() {
			return streamPromise;
		}

		public StreamSupplier<CrdtData<K, S>> getStream() {
			return StreamSupplier.ofPromise(streamPromise);
		}

		public Promise<Long> getTokenPromise() {
			return tokenPromise;
		}
	}
}
