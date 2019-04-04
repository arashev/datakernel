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
import io.global.common.CryptoUtils;

import java.util.Arrays;

import static io.global.common.CryptoUtils.sha256;

public final class CommitId {
	private static final CommitId ROOT = new CommitId(new byte[]{});

	private final byte[] bytes;

	private CommitId(byte[] bytes) {
		this.bytes = bytes;
	}

	public static CommitId ofRoot() {
		return ROOT;
	}

	public static CommitId ofBytes(byte[] bytes) {
		return new CommitId(bytes);
	}

	public static CommitId parse(byte[] bytes) throws ParseException {
		return new CommitId(bytes);
	}

	public static CommitId ofCommitData(byte[] bytes) {
		return new CommitId(sha256(bytes));
	}

	public boolean isRoot() {
		return this.bytes.length == 0;
	}

	public byte[] toBytes() {
		return bytes;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CommitId that = (CommitId) o;
		return Arrays.equals(bytes, that.bytes);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(bytes);
	}

	@Override
	public String toString() {
		if (isRoot()) {
			return "ROOT";
		}
		return CryptoUtils.toHexString(bytes);
	}
}
