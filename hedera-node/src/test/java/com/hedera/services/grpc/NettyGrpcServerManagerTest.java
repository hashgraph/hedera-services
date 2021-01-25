package com.hedera.services.grpc;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.legacy.netty.NettyServerManager;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.netty.NettyServerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.*;

class NettyGrpcServerManagerTest {
	int port = 8080;
	int tlsPort = port + 1;
	Server server;
	Server tlsServer;
	Consumer<Thread> hookAdder;
	Consumer<String> println;
	NettyServerBuilder nettyBuilder;
	NettyServerBuilder tlsBuilder;
	NettyServerManager nettyManager;
	BindableService a, b, c;
	List<BindableService> bindableServices;
	List<ServerServiceDefinition> serviceDefinitions;
	ServerServiceDefinition d;

	NettyGrpcServerManager subject;

	@BeforeEach
	private void setup() throws Exception {
		server = mock(Server.class);
		tlsServer = mock(Server.class);
		a = mock(BindableService.class);
		b = mock(BindableService.class);
		c = mock(BindableService.class);
		bindableServices = List.of(a, b, c);
		d = mock(ServerServiceDefinition.class);
		serviceDefinitions = List.of(d);

		nettyBuilder = mock(NettyServerBuilder.class);
		given(nettyBuilder.addService(any(BindableService.class))).willReturn(nettyBuilder);
		given(nettyBuilder.addService(any(ServerServiceDefinition.class))).willReturn(nettyBuilder);
		given(nettyBuilder.build()).willReturn(server);

		tlsBuilder = mock(NettyServerBuilder.class);
		given(tlsBuilder.addService(any(BindableService.class))).willReturn(tlsBuilder);
		given(tlsBuilder.addService(any(ServerServiceDefinition.class))).willReturn(tlsBuilder);
		given(tlsBuilder.build()).willReturn(tlsServer);

		nettyManager = mock(NettyServerManager.class);
		given(nettyManager.buildNettyServer(port, false)).willReturn(nettyBuilder);
		given(nettyManager.buildNettyServer(tlsPort, true)).willReturn(tlsBuilder);

		println = mock(Consumer.class);
		hookAdder = mock(Consumer.class);

		subject = new NettyGrpcServerManager(hookAdder, nettyManager, bindableServices, serviceDefinitions);
	}

	@Test
	public void buildsAndAddsHookNonTlsOnNonExistingCertOrKey() throws Exception {
		// setup:
		given(nettyManager.buildNettyServer(tlsPort, true)).willThrow(new FileNotFoundException());
		ArgumentCaptor<Thread> captor = ArgumentCaptor.forClass(Thread.class);

		willDoNothing().given(hookAdder).accept(captor.capture());

		// when:
		subject.start(port, tlsPort, println);

		// then:
		verifyBuilder(nettyBuilder);
		verifyNoInteractions(tlsBuilder);
		// and:
		verify(server).start();
		verifyNoInteractions(tlsServer);
		// and:
		try {
			captor.getValue().run();
		} catch (Exception ignore) {}
		// and:
		verify(server).awaitTermination(anyLong(), any());
	}

	@Test
	public void buildsAndAddsHookAsExpected() throws Exception {
		// setup:
		ArgumentCaptor<Thread> captor = ArgumentCaptor.forClass(Thread.class);

		willDoNothing().given(hookAdder).accept(captor.capture());

		// when:
		subject.start(port, tlsPort, println);

		// then:
		verifyBuilder(nettyBuilder);
		verifyBuilder(tlsBuilder);
		// and:
		verify(server).start();
		verify(tlsServer).start();
		// and:
		try {
			captor.getValue().run();
		} catch (Exception ignore) {}
		// and:
		verify(server).awaitTermination(anyLong(), any());
		verify(tlsServer).awaitTermination(anyLong(), any());
	}

	private void verifyBuilder(NettyServerBuilder builder) {
		verify(builder).addService(a);
		verify(builder).addService(b);
		verify(builder).addService(c);
		verify(builder).addService(d);
		verify(builder).build();
	}

	@Test
	public void throwsIseOnProblem() {
		willThrow(RuntimeException.class).given(hookAdder).accept(any());

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.start(port, tlsPort, println));
	}

	@Test
	public void catchesInterruptedException() throws Exception {
		// setup:
		ArgumentCaptor<Thread> captor = ArgumentCaptor.forClass(Thread.class);

		willDoNothing().given(hookAdder).accept(captor.capture());
		// and:
		given(server.awaitTermination(anyLong(), any())).willAnswer(ignore -> {
			Thread.sleep(5_000L);
			return null;
		});

		// when:
		subject.start(port, tlsPort, println);

		// then:
		captor.getValue().start();
		Thread.sleep(10L);
		assertDoesNotThrow(() -> captor.getValue().interrupt());
	}
}
