package com.hedera.services.grpc;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.netty.NettyServerBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


public class NettyGrpcServerManager implements GrpcServerManager {
	public static final Logger log = LogManager.getLogger(NettyGrpcServerManager.class);

	private static final long TIME_TO_AWAIT_TERMINATION = 5;

	private Server server;
	private Server tlsServer;
	private final Consumer<Thread> hookAdder;
	private final List<BindableService> bindableServices;
	private final ConfigDrivenNettyFactory nettyBuilder;
	private final List<ServerServiceDefinition> serviceDefinitions;

	public NettyGrpcServerManager(
			Consumer<Thread> hookAdder,
			List<BindableService> bindableServices,
			ConfigDrivenNettyFactory nettyBuilder,
			List<ServerServiceDefinition> serviceDefinitions
	) {
		this.hookAdder = hookAdder;
		this.nettyBuilder = nettyBuilder;
		this.bindableServices = bindableServices;
		this.serviceDefinitions = serviceDefinitions;
	}

	@Override
	public void start(int port, int tlsPort, Consumer<String> println) {
		try {
			hookAdder.accept(new Thread(() -> terminateNetty(port, tlsPort, println)));
			server = startOneNettyServer(false, port, println);
			tlsServer = startOneNettyServer(true, tlsPort, println);
		} catch (FileNotFoundException fnfe) {
			tlsServer = null;
			String message = nettyAction("Could not start", true, tlsPort, false);
			log.warn("{} ({}).", message, fnfe.getMessage());
			println.accept(message);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private Server startOneNettyServer(boolean sslEnabled, int port, Consumer<String> println) throws Exception {
		println.accept(nettyAction("Starting", sslEnabled, port, true));

		NettyServerBuilder builder = nettyBuilder.builderFor(port, sslEnabled);
		bindableServices.forEach(builder::addService);
		serviceDefinitions.forEach(builder::addService);
		Server server = builder.build();
		server.start();

		println.accept(nettyAction("...done starting", sslEnabled, port, false));

		return server;
	}

	private void terminateNetty(int port, int tlsPort, Consumer<String> println) {
		terminateOneNettyServer(server, false, port, println);
		terminateOneNettyServer(tlsServer, true, tlsPort, println);
	}

	private void terminateOneNettyServer(Server server, boolean tlsSupport, int port, Consumer<String> println) {
		if (null == server) {
			return;
		}

		try {
			println.accept(nettyAction("Terminating", tlsSupport, port, true));
			server.awaitTermination(TIME_TO_AWAIT_TERMINATION, TimeUnit.SECONDS);
			println.accept(nettyAction("...done terminating", tlsSupport, port, false));
		} catch (InterruptedException ie) {
			log.warn("Interrupted while waiting for Netty to terminate on port {}!", port, ie);
		}
	}

	private String nettyAction(String action, boolean tlsSupport, int port, boolean isOpening) {
		return String.format("%s Netty%s on port %d%s"
				, action
				, tlsSupport ? " with TLS support" : ""
				, port
				, isOpening ? "..." : ".");
	}
}
