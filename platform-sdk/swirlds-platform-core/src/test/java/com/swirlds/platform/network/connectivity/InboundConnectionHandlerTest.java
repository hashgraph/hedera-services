/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.network.connectivity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.network.ByteConstants;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.network.NetworkPeerIdentifier;
import com.swirlds.platform.network.PeerInfo;
import com.swirlds.platform.network.SocketConfig_;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookGenerator;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookGenerator.WeightDistributionStrategy;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class InboundConnectionHandlerTest {
    @Test
    @DisplayName("inbound connection successfully identifies a peer")
    void handleInboundConnectionTestOnePeer() throws IOException {
        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(getConfig())
                .build();
        final AddressBook addressBook = new RandomAddressBookGenerator(new Random())
                .setSize(5)
                .setWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                .setHashStrategy(RandomAddressBookGenerator.HashStrategy.FAKE_HASH)
                .build();

        final Random r = new Random();
        final NodeId thisNodeId = addressBook.getNodeId(r.nextInt(5));
        final NodeId otherNodeId = addressBook.getNodeId(r.nextInt(5));
        final ConnectionTracker ct = Mockito.mock(ConnectionTracker.class);
        final List<PeerInfo> peerInfoList = Utilities.createPeerInfoList(addressBook, thisNodeId);
        final Certificate[] certificates = new Certificate[1];

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();

        final SerializableDataOutputStream outStream = new SerializableDataOutputStream(byteOut);
        outStream.writeInt(ByteConstants.COMM_CONNECT);

        final NetworkPeerIdentifier identifier = mock(NetworkPeerIdentifier.class);
        final SSLSocket clientSocket = Mockito.mock(SSLSocket.class);
        final SSLSession sslSession = Mockito.mock(SSLSession.class);
        final SocketFactory socketFactory = Mockito.mock(SocketFactory.class);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(byteOut.toByteArray());
        final PeerInfo peerInfo = new PeerInfo(otherNodeId, "other", "hostname", mock(Certificate.class));

        doAnswer(i -> inputStream).when(clientSocket).getInputStream();
        doAnswer(i -> outStream).when(clientSocket).getOutputStream();
        doAnswer(i -> certificates).when(sslSession).getPeerCertificates();
        doAnswer(i -> sslSession).when(clientSocket).getSession();
        doAnswer(i -> true).when(clientSocket).isConnected();
        doAnswer(i -> true).when(clientSocket).isBound();
        doAnswer(i -> false).when(clientSocket).isClosed();
        doAnswer(i -> clientSocket).when(socketFactory).createClientSocket(anyString(), anyInt());
        doAnswer(i -> peerInfo).when(identifier).identifyTlsPeer(any(), anyList());

        final InterruptableConsumer<Connection> ic = inboundConn -> {
            Assertions.assertNotNull(inboundConn);
            Assertions.assertFalse(inboundConn.isOutbound());
            Assertions.assertTrue(inboundConn.connected());
            Assertions.assertNotNull(inboundConn.getOtherId());
            inboundConn.disconnect();
        };
        final InboundConnectionHandler inbound =
                new InboundConnectionHandler(platformContext, ct, identifier, thisNodeId, ic, Time.getCurrent());
        inbound.handle(clientSocket, peerInfoList);
    }

    @Test
    @DisplayName("inbound connection unsuccessfully identifies a peer, connection is dropped")
    void handleInboundConnectionTestNoPeer() throws IOException {
        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(getConfig())
                .build();
        final AddressBook addressBook = new RandomAddressBookGenerator(new Random())
                .setSize(5)
                .setWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                .setHashStrategy(RandomAddressBookGenerator.HashStrategy.FAKE_HASH)
                .build();

        final Random r = new Random();
        final NodeId thisNodeId = addressBook.getNodeId(r.nextInt(5));
        final ConnectionTracker ct = Mockito.mock(ConnectionTracker.class);

        final NetworkPeerIdentifier identifier = mock(NetworkPeerIdentifier.class);
        final SSLSocket clientSocket = Mockito.mock(SSLSocket.class);
        doAnswer(i -> null).when(identifier).identifyTlsPeer(any(), anyList());

        final InboundConnectionHandler inbound =
                new InboundConnectionHandler(platformContext, ct, identifier, thisNodeId, x -> {
                }, Time.getCurrent());
        inbound.handle(clientSocket, Collections.emptyList());
        try {
            verify(clientSocket, times(1)).close();
        } catch (final IOException e) {
            // do nothing. Here to keep the compiler happy
        }
    }

    @NonNull
    private static Configuration getConfig() {
        return new TestConfigBuilder()
                .withValue(SocketConfig_.BUFFER_SIZE, "100")
                .withValue(SocketConfig_.GZIP_COMPRESSION, false)
                .getOrCreateConfig();
    }
}
