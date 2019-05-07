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

package io.global.ot.api;

import io.datakernel.exception.ParseException;
import io.global.common.Hash;
import io.global.common.api.EncryptedData;

import java.util.Map;
import java.util.Set;

import static io.datakernel.util.Preconditions.checkNotNull;

public final class RawCommit {
	private final int epoch;
	private final Map<CommitId, Long> parents;
	private final EncryptedData encryptedDiffs;
	private final Hash simKeyHash;
	private final long timestamp;

	// region creators
	private RawCommit(int epoch, Map<CommitId, Long> parents, EncryptedData encryptedDiffs, Hash simKeyHash,
			long timestamp) {
		this.epoch = epoch;
		this.parents = checkNotNull(parents);
		this.encryptedDiffs = checkNotNull(encryptedDiffs);
		this.simKeyHash = checkNotNull(simKeyHash);
		this.timestamp = timestamp;
	}

	public static RawCommit of(int epoch, Map<CommitId, Long> parents, EncryptedData encryptedDiffs, Hash simKeyHash,
			long timestamp) {
		return new RawCommit(epoch, parents, encryptedDiffs, simKeyHash, timestamp);
	}

	public static RawCommit parse(int epoch, Map<CommitId, Long> parents, EncryptedData encryptedDiffs, Hash simKeyHash,
			long timestamp) throws ParseException {
		return new RawCommit(epoch, parents, encryptedDiffs, simKeyHash, timestamp);
	}
	// endregion

	public int getEpoch() {
		return epoch;
	}

	public Set<CommitId> getParents() {
		return parents.keySet();
	}

	public Map<CommitId, Long> getParentLevels() {
		return parents;
	}

	public EncryptedData getEncryptedDiffs() {
		return encryptedDiffs;
	}

	public Hash getSimKeyHash() {
		return simKeyHash;
	}

	public long getLevel() {
		return parents.values().stream().mapToLong(v -> v).max().orElse(0L) + 1L;
	}

	public long getTimestamp() {
		return timestamp;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		RawCommit rawCommit = (RawCommit) o;

		if (timestamp != rawCommit.timestamp) return false;
		if (!parents.equals(rawCommit.parents)) return false;
		if (!encryptedDiffs.equals(rawCommit.encryptedDiffs)) return false;
		return simKeyHash.equals(rawCommit.simKeyHash);
	}

	@Override
	public int hashCode() {
		int result = parents.hashCode();
		result = 31 * result + encryptedDiffs.hashCode();
		result = 31 * result + simKeyHash.hashCode();
		result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
		return result;
	}
}
