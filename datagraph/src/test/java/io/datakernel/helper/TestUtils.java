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

package io.datakernel.helper;

import io.datakernel.eventloop.Eventloop;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

public final class TestUtils {
	private TestUtils() {}

	public static Matcher<Eventloop> doesntHaveFatals() {
		return new BaseMatcher<Eventloop>() {
			@Override
			public boolean matches(Object item) {
				Eventloop eventloop = (Eventloop) item;
				return eventloop.getStats().getErrorStats().getFatalErrors().getTotal() == 0;
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("Eventloop doesn't contain fatal error");
			}

			@Override
			public void describeMismatch(Object item, Description description) {
				description.appendText("Eventloop contains at least one fatal error");
			}
		};
	}
}
