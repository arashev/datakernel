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

package io.datakernel.http;

import org.jetbrains.annotations.Nullable;

public final class QueryParameter {
	private final String key;
	private final String value;

	QueryParameter(String key, @Nullable String value) {
		this.key = key;
		this.value = value;
	}

	public String getKey() {
		return key;
	}

	@Nullable
	public String getValue() {
		return value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		QueryParameter queryParameter = (QueryParameter) o;

		if (key != null ? !key.equals(queryParameter.key) : queryParameter.key != null) return false;
		return value != null ? value.equals(queryParameter.value) : queryParameter.value == null;
	}

	@Override
	public int hashCode() {
		int result = key != null ? key.hashCode() : 0;
		result = 31 * result + (value != null ? value.hashCode() : 0);
		return result;
	}
}
