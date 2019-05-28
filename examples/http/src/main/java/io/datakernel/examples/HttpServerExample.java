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

package io.datakernel.examples;

import io.datakernel.async.Promise;
import io.datakernel.config.Config;
import io.datakernel.config.ConfigModule;
import io.datakernel.di.module.AbstractModule;
import io.datakernel.di.module.Module;
import io.datakernel.di.module.Provides;
import io.datakernel.http.AsyncServlet;
import io.datakernel.http.HttpResponse;
import io.datakernel.launcher.Launcher;
import io.datakernel.launchers.http.HttpServerLauncher;
import io.datakernel.service.ServiceGraphModule;

import java.util.Collection;

import static io.datakernel.bytebuf.ByteBufStrings.encodeAscii;
import static java.lang.Boolean.parseBoolean;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

/**
 * HTTP simple server example.
 * Sends back a greeting and received data.
 */
public final class HttpServerExample extends HttpServerLauncher {
	@Override
	protected Collection<Module> getOverrideModules() {
		return asList(
				ConfigModule.create(Config.create()
						.with("http.listenAddresses", "5588")
				),
				ServiceGraphModule.defaultInstance()
		);
	}

	@Override
	protected Collection<Module> getBusinessLogicModules() {
		return singletonList(
				new AbstractModule() {
					@Provides
					AsyncServlet servlet() {
						return request -> Promise.of(HttpResponse.ok200()
								.withBody(encodeAscii("Hello World!")));
					}
				}
		);
	}

	@Override
	protected void onStart() {
		System.out.println("Server is running");
		System.out.println("You can connect from browser by visiting 'http://localhost:5588/' or by running HttpClientExample");
	}

	public static void main(String[] args) throws Exception {
		Launcher launcher = new HttpServerExample();
		launcher.launch(args);
	}
}
