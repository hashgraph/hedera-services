/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.grpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.context.properties.Profile;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import java.io.PrintStream;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class GrpcStarterTest {
    private final int port = 50211;
    private final int tlsPort = 50212;
    private final NodeId nodeId = new NodeId(false, 123L);

    @Mock private Address nodeAddress;
    @Mock private AddressBook addressBook;
    @Mock private GrpcServerManager grpcServerManager;
    @Mock private NodeLocalProperties nodeLocalProperties;
    @Mock private PrintStream console;

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private GrpcStarter subject;

    @BeforeEach
    void setUp() {
        subject =
                new GrpcStarter(
                        nodeId,
                        grpcServerManager,
                        nodeLocalProperties,
                        () -> addressBook,
                        Optional.of(console));
    }

    @Test
    void startsUnconditionallyWithProdProfile() {
        withPorts();

        given(nodeLocalProperties.activeProfile()).willReturn(Profile.PROD);

        // when:
        subject.startIfAppropriate();

        // then:
        verify(grpcServerManager).start(intThat(i -> i == port), intThat(j -> j == tlsPort), any());
        // and:
        assertThat(
                logCaptor.infoLogs(),
                contains(
                        equalTo("TLS is turned on by default on node 123"),
                        equalTo("Active profile: PROD")));
    }

    @Test
    void weirdlyJustWarnsOnTestProfile() {
        withPorts();

        given(nodeLocalProperties.activeProfile()).willReturn(Profile.TEST);

        // when:
        subject.startIfAppropriate();

        // then:
        verifyNoInteractions(grpcServerManager);
        // and:
        assertThat(
                logCaptor.warnLogs(),
                contains(equalTo("No Netty config for profile TEST, skipping gRPC startup")));
    }

    @Test
    void startsIfBlessedOnDevProfileOnlyOneNodeListening() {
        withPorts();

        given(addressBook.getAddress(nodeId.getId())).willReturn(nodeAddress);
        given(nodeLocalProperties.activeProfile()).willReturn(Profile.DEV);
        given(nodeLocalProperties.devOnlyDefaultNodeListens()).willReturn(true);
        given(nodeAddress.getMemo()).willReturn("0.0.3");
        given(nodeLocalProperties.devListeningAccount()).willReturn("0.0.3");

        // when:
        subject.startIfAppropriate();

        // then:
        verify(grpcServerManager).start(intThat(i -> i == port), intThat(j -> j == tlsPort), any());
    }

    @Test
    void doesntStartIfNotBlessedOnDevProfileOnlyOneNodeListening() {
        withPorts();

        given(addressBook.getAddress(nodeId.getId())).willReturn(nodeAddress);
        given(nodeLocalProperties.activeProfile()).willReturn(Profile.DEV);
        given(nodeLocalProperties.devOnlyDefaultNodeListens()).willReturn(true);
        given(nodeAddress.getMemo()).willReturn("0.0.4");
        given(nodeLocalProperties.devListeningAccount()).willReturn("0.0.3");

        // when:
        subject.startIfAppropriate();

        // then:
        verifyNoInteractions(grpcServerManager);
    }

    @Test
    void startsIfBlessedOnDevProfileAllNodesListening() {
        withPorts();

        given(nodeLocalProperties.activeProfile()).willReturn(Profile.DEV);
        given(nodeAddress.getMemo()).willReturn("0.0.3");
        given(nodeLocalProperties.devListeningAccount()).willReturn("0.0.3");
        given(addressBook.getAddress(nodeId.getId())).willReturn(nodeAddress);

        // when:
        subject.startIfAppropriate();

        // then:
        verify(grpcServerManager).start(intThat(i -> i == port), intThat(j -> j == tlsPort), any());
    }

    @Test
    void startsIfUnblessedOnDevProfileAllNodesListening() {
        withPorts();

        given(addressBook.getAddress(nodeId.getId())).willReturn(nodeAddress);
        given(nodeLocalProperties.activeProfile()).willReturn(Profile.DEV);
        given(nodeAddress.getMemo()).willReturn("0.0.4");
        given(nodeAddress.getPortExternalIpv4()).willReturn(50666);
        given(nodeLocalProperties.devListeningAccount()).willReturn("0.0.3");

        // when:
        subject.startIfAppropriate();

        // then:
        verify(grpcServerManager)
                .start(intThat(i -> i == port + 666), intThat(j -> j == tlsPort + 666), any());
    }

    @Test
    void logWithConsoleInfoWorks() {
        // when:
        subject.logInfoWithConsoleEcho("NOOP");

        // then:
        assertThat(logCaptor.infoLogs(), contains(equalTo("NOOP")));
        verify(console).println("NOOP");
    }

    private void withPorts() {
        given(nodeLocalProperties.port()).willReturn(port);
        given(nodeLocalProperties.tlsPort()).willReturn(tlsPort);
    }
}
