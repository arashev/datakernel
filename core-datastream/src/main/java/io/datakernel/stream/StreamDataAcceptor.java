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

package io.datakernel.stream;

/**
 * Callback which handle received results from other stream
 *
 * @param <T> type of received item
 */
@FunctionalInterface
public interface StreamDataAcceptor<T> {
	/**
	 * Method which calling after each receiving result
	 *
	 * @param item received item
	 */
	void accept(T item);
}