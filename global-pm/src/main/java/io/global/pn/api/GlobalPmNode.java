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

package io.global.pn.api;

import io.datakernel.async.Promise;
import io.datakernel.csp.ChannelConsumer;
import io.datakernel.csp.ChannelSupplier;
import io.global.common.PubKey;
import io.global.common.SignedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface GlobalPmNode {

	@NotNull
	Promise<Void> send(PubKey space, SignedData<RawMessage> message);

	@NotNull
	default Promise<ChannelConsumer<SignedData<RawMessage>>> multisend(PubKey receiver) {
		return Promise.of(ChannelConsumer.of(message -> send(receiver, message)));
	}

	@NotNull
	Promise<@Nullable SignedData<RawMessage>> poll(PubKey space);

	default Promise<ChannelSupplier<SignedData<RawMessage>>> multipoll(PubKey receiver) {
		return Promise.of(ChannelSupplier.of(() -> poll(receiver)));
	}

	Promise<Void> drop(PubKey space, SignedData<Long> id);

	@NotNull
	default Promise<ChannelConsumer<SignedData<Long>>> multidrop(PubKey receiver) {
		return Promise.of(ChannelConsumer.of(id -> drop(receiver, id)));
	}
}
