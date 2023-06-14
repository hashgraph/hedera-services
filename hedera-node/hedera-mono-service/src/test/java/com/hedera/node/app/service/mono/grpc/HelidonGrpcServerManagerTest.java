/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.node.app.service.mono.grpc;

import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.grpc.HelidonGrpcServerManager.GrpcServerSource;
import com.hedera.node.app.service.mono.utils.Pause;
import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import io.helidon.common.configurable.ResourceException;
import io.helidon.grpc.server.GrpcServer;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HelidonGrpcServerManagerTest {
    private int startRetries = 3;
    private long startRetryIntervalMs = 10L;
    private int port = 8080;
    private int tlsPort = port + 1;
    private Set<BindableService> bindableServices;

    @Mock(strictness = Strictness.LENIENT)
    private Pause mockPause;

    @Mock(strictness = Strictness.LENIENT)
    private Consumer<Thread> hookAdder;

    @Mock(strictness = Strictness.LENIENT)
    private Consumer<String> println;

    @Mock(strictness = Strictness.LENIENT)
    private NodeLocalProperties nodeProperties;

    @Mock(strictness = Strictness.LENIENT)
    private BindableService mockServiceOne;

    @Mock(strictness = Strictness.LENIENT)
    private BindableService mockServiceTwo;

    @Mock(strictness = Strictness.LENIENT)
    private BindableService mockServiceThree;

    @Mock(strictness = Strictness.LENIENT)
    private GrpcServerSource mockServerSource;

    @Mock(strictness = Strictness.LENIENT)
    private GrpcServer mockServer;

    @Mock(strictness = Strictness.LENIENT)
    private GrpcServer mockTlsServer;

    private HelidonGrpcServerManager subject;

    @SuppressWarnings("AutoBoxing")
    @BeforeEach
    void setup() throws Exception {
        Path fakeCertPath =
                Path.of(ClassLoader.getSystemResource("test-hedera.crt").toURI());
        Path fakeKeyPath =
                Path.of(ClassLoader.getSystemResource("test-hedera.key").toURI());
        BDDMockito.given(mockServiceOne.bindService())
                .willReturn(ServerServiceDefinition.builder("MachOne").build());
        BDDMockito.given(mockServiceTwo.bindService())
                .willReturn(ServerServiceDefinition.builder("MachTwo").build());
        BDDMockito.given(mockServiceThree.bindService())
                .willReturn(ServerServiceDefinition.builder("MachThree").build());
        bindableServices = Set.of(mockServiceOne, mockServiceTwo, mockServiceThree);

        BDDMockito.given(mockPause.forMs(ArgumentMatchers.anyLong())).willReturn(true);

        BDDMockito.given(nodeProperties.nettyTlsCrtPath()).willReturn(fakeCertPath.toString());
        BDDMockito.given(nodeProperties.nettyTlsKeyPath()).willReturn(fakeKeyPath.toString());
        BDDMockito.given(nodeProperties.nettyStartRetries()).willReturn(startRetries);
        BDDMockito.given(nodeProperties.nettyStartRetryIntervalMs()).willReturn(startRetryIntervalMs);

        subject = new HelidonGrpcServerManager(bindableServices, hookAdder, nodeProperties, mockServerSource);
    }

    @Test
    void retriesStartingTilSuccess() throws Exception {
        final String ExpectedExceptionMessage = "Expected : retriesStartingTilSuccess";
        // setup:
        RuntimeException exceptionToThrow = new RuntimeException(ExpectedExceptionMessage);
        BDDMockito.given(mockTlsServer.start())
                .willThrow(exceptionToThrow, exceptionToThrow)
                .willReturn(null);
        BDDMockito.given(mockServerSource.getServer(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .willReturn(mockTlsServer);
        // when:
        GrpcServer server = subject.startOneServer(true, port, ignore -> {}, mockPause);
        // then:
        BDDAssertions.assertThat(server).isNotNull();
        Mockito.verify(mockPause, Mockito.times(2)).forMs(ArgumentMatchers.anyLong());
        Mockito.verify(server, Mockito.times(3)).start();
    }

    @Test
    void givesUpIfMaxRetriesExhaustedAndPropagatesException() throws Exception {
        final String ExpectedExceptionMessage = "Expected : givesUpIfMaxRetriesExhaustedAndPropagatesException";
        BDDMockito.given(mockServer.start()).willThrow(new RuntimeException(ExpectedExceptionMessage));
        BDDMockito.given(mockServerSource.getServer(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .willReturn(mockServer);

        Assertions.assertThrows(
                RuntimeException.class, () -> subject.startOneServer(false, port, ignore -> {}, mockPause));
        Mockito.verify(mockPause, Mockito.times(startRetries)).forMs(startRetryIntervalMs);
    }

    @Test
    void neverRetriesIfZeroRetriesSet() throws Exception {
        final String ExpectedExceptionMessage = "Expected : neverRetriesIfZeroRetriesSet";
        // setup:
        BDDMockito.given(nodeProperties.nettyStartRetries()).willReturn(0);
        BDDMockito.given(mockServer.start()).willThrow(new RuntimeException(ExpectedExceptionMessage));
        BDDMockito.given(mockServerSource.getServer(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .willReturn(mockServer);
        // expect:
        Assertions.assertThrows(
                RuntimeException.class, () -> subject.startOneServer(false, port, ignore -> {}, mockPause));
        // then:
        Mockito.verify(mockPause, Mockito.never()).forMs(startRetryIntervalMs);
    }

    @Test
    void buildsAndAddsHookNonTlsOnNonExistingCert() throws Exception {
        final String ExpectedExceptionMessage = "Expected : buildsAndAddsHookNonTlsOnNonExistingCert";
        ArgumentCaptor<Thread> captor = ArgumentCaptor.forClass(Thread.class);
        BDDMockito.willDoNothing().given(hookAdder).accept(captor.capture());
        BDDMockito.given(nodeProperties.nettyTlsCrtPath()).willThrow(new ResourceException(ExpectedExceptionMessage));
        BDDMockito.given(mockServerSource.getServer(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .willCallRealMethod();
        // when:
        List<GrpcServer> servers = subject.start(port, tlsPort, println);
        // and:
        BDDAssertions.assertThat(servers).isNotNull().hasSize(1); // no tlsServer for this test
        BDDAssertions.assertThat(servers.get(0)).isNotNull();
        // and:
        Assertions.assertDoesNotThrow(() -> captor.getValue().run());
    }

    @Test
    void buildsAndAddsHookNonTlsOnNonExistingKey() throws Exception {
        final String ExpectedExceptionMessage = "Expected : buildsAndAddsHookNonTlsOnNonExistingKey";
        ArgumentCaptor<Thread> captor = ArgumentCaptor.forClass(Thread.class);
        BDDMockito.willDoNothing().given(hookAdder).accept(captor.capture());
        BDDMockito.given(nodeProperties.nettyTlsKeyPath()).willThrow(new ResourceException(ExpectedExceptionMessage));
        BDDMockito.given(mockServerSource.getServer(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .willCallRealMethod();
        // when:
        List<GrpcServer> servers = subject.start(port, tlsPort, println);
        // and:
        BDDAssertions.assertThat(servers).isNotNull().hasSize(1); // no tlsServer for this test
        BDDAssertions.assertThat(servers.get(0)).isNotNull();
        // and:
        Assertions.assertDoesNotThrow(() -> captor.getValue().run());
    }

    @SuppressWarnings("CallToThreadRun")
    @Test
    void buildsAndAddsHookAsExpected() throws Exception {
        // setup:
        ArgumentCaptor<Thread> captor = ArgumentCaptor.forClass(Thread.class);
        BDDMockito.willDoNothing().given(hookAdder).accept(captor.capture());
        BDDMockito.given(mockServerSource.getServer(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .willCallRealMethod();
        // when:
        List<GrpcServer> servers = subject.start(port, tlsPort, println);
        // and:
        BDDAssertions.assertThat(servers).isNotNull().hasSize(2);
        BDDAssertions.assertThat(servers.get(0)).isNotNull();
        BDDAssertions.assertThat(servers.get(1)).isNotNull();
        // and:
        Assertions.assertDoesNotThrow(() -> captor.getValue().run());
    }

    @Test
    void throwsRteOnProblem() {
        BDDMockito.willThrow(RuntimeException.class).given(hookAdder).accept(ArgumentMatchers.any());
        BDDMockito.given(mockServerSource.getServer(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .willCallRealMethod();
        // expect:
        Assertions.assertThrows(RuntimeException.class, () -> subject.start(port, tlsPort, println));
    }

    @SuppressWarnings({"CallToThreadRun", "unchecked"})
    @Test
    void catchesInterruptedException() throws Exception {
        // setup:
        // a Mock throwing InterruptedException is hard to setup
        CompletableFuture<GrpcServer> mockFuture = Mockito.mock(CompletableFuture.class);
        BDDMockito.given(mockFuture.toCompletableFuture()).willReturn(mockFuture);
        BDDMockito.given(mockFuture.get()).willThrow(new InterruptedException("Test Interrupt"));

        ArgumentCaptor<Thread> captor = ArgumentCaptor.forClass(Thread.class);
        BDDMockito.willDoNothing().given(hookAdder).accept(captor.capture());
        BDDMockito.given(mockServer.shutdown()).willReturn(mockFuture);
        BDDMockito.given(mockServer.start()).willReturn(mockFuture);
        BDDMockito.given(mockServerSource.getServer(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .willReturn(mockServer);
        // Cannot have two servers (nigh impossible to set name correctly, and GRPC requires unique names).
        BDDMockito.given(nodeProperties.nettyTlsCrtPath()).willThrow(new ResourceException("No TLS for this Test"));
        // when:
        List<GrpcServer> servers = subject.start(port, tlsPort, println);
        BDDAssertions.assertThat(servers).isNotNull().hasSize(1);
        // then:
        BDDAssertions.assertThat(servers.get(0)).isEqualTo(mockServer);
        Assertions.assertThrows(
                InterruptedException.class,
                () -> servers.get(0).shutdown().toCompletableFuture().get());
        Assertions.assertDoesNotThrow(() -> captor.getValue().run());
    }
}
