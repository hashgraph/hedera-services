/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.RandomAddressBookGenerator.WeightDistributionStrategy;
import com.swirlds.platform.Connection;
import com.swirlds.platform.SettingsProvider;
import com.swirlds.platform.SocketConnection;
import com.swirlds.platform.network.ByteConstants;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.network.connection.NotConnectedConnection;
import com.swirlds.platform.network.connectivity.OutboundConnectionCreator;
import com.swirlds.platform.network.connectivity.SocketFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OutboundConnectionCreatorTest {
    @Test
    void createConnectionTest() throws IOException, ConstructableRegistryException {

        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(BasicSoftwareVersion.class, BasicSoftwareVersion::new));

        final int numNodes = 10;
        final Random r = new Random();
        final AddressBook addressBook = new RandomAddressBookGenerator(r)
                .setSize(numNodes)
                .setWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                .setHashStrategy(RandomAddressBookGenerator.HashStrategy.FAKE_HASH)
                .setSequentialIds(true)
                .build();
        final NodeId thisNode = NodeId.createMain(r.nextInt(10));
        final NodeId otherNode = NodeId.createMain(r.nextInt(10));

        final AtomicBoolean connected = new AtomicBoolean(true);
        final Socket socket = mock(Socket.class);
        doAnswer(i -> connected.get()).when(socket).isConnected();
        doAnswer(i -> connected.get()).when(socket).isBound();
        doAnswer(i -> !connected.get()).when(socket).isClosed();
        doAnswer(i -> {
                    connected.set(false);
                    return null;
                })
                .when(socket)
                .close();

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);
        out.writeSerializable(new BasicSoftwareVersion(1), true);
        out.writeInt(ByteConstants.COMM_CONNECT);
        out.close();

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(byteOut.toByteArray());
        doAnswer(i -> inputStream).when(socket).getInputStream();
        doAnswer(i -> mock(OutputStream.class)).when(socket).getOutputStream();
        final SocketFactory socketFactory = mock(SocketFactory.class);
        doAnswer(i -> socket).when(socketFactory).createClientSocket(any(), anyInt());

        final SettingsProvider settings = mock(SettingsProvider.class);
        doAnswer(i -> 100).when(settings).connectionStreamBufferSize();

        final OutboundConnectionCreator occ = new OutboundConnectionCreator(
                thisNode,
                settings,
                mock(ConnectionTracker.class),
                socketFactory,
                addressBook,
                true,
                new BasicSoftwareVersion(1));

        Connection connection = occ.createConnection(otherNode);
        assertTrue(connection instanceof SocketConnection, "the returned connection should be a socket connection");
        assertEquals(thisNode, connection.getSelfId(), "self ID should match supplied ID");
        assertEquals(otherNode, connection.getOtherId(), "other ID should match supplied ID");
        assertTrue(connection.connected(), "a new connection should be connected");
        connection.disconnect();
        assertFalse(connection.connected(), "should not be connected after calling disconnect()");

        // test exceptions
        final ByteArrayOutputStream byteOut2 = new ByteArrayOutputStream();
        final SerializableDataOutputStream out2 = new SerializableDataOutputStream(byteOut2);
        out2.writeSerializable(new BasicSoftwareVersion(1), true);
        out2.writeInt(0);
        out2.close();

        final ByteArrayInputStream badInputStream = new ByteArrayInputStream(byteOut2.toByteArray());

        doAnswer(i -> badInputStream).when(socket).getInputStream();
        connection = occ.createConnection(otherNode);
        assertTrue(
                connection instanceof NotConnectedConnection,
                "the returned connection should be a fake not connected connection");

        Mockito.doThrow(SocketException.class).when(socketFactory).createClientSocket(any(), anyInt());
        connection = occ.createConnection(otherNode);
        assertTrue(
                connection instanceof NotConnectedConnection,
                "the returned connection should be a fake not connected connection");

        Mockito.doThrow(IOException.class).when(socketFactory).createClientSocket(any(), anyInt());
        connection = occ.createConnection(otherNode);
        assertTrue(
                connection instanceof NotConnectedConnection,
                "the returned connection should be a fake not connected connection");

        Mockito.doThrow(RuntimeException.class).when(socketFactory).createClientSocket(any(), anyInt());
        connection = occ.createConnection(otherNode);
        assertTrue(
                connection instanceof NotConnectedConnection,
                "the returned connection should be a fake not connected connection");
    }

    @Test
    @DisplayName("Mismatched Version Test")
    void mismatchedVersionTest() throws IOException, ConstructableRegistryException {

        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(BasicSoftwareVersion.class, BasicSoftwareVersion::new));

        final int numNodes = 10;
        final Random r = new Random();
        final AddressBook addressBook = new RandomAddressBookGenerator(r)
                .setSize(numNodes)
                .setWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                .setHashStrategy(RandomAddressBookGenerator.HashStrategy.FAKE_HASH)
                .setSequentialIds(true)
                .build();
        final NodeId thisNode = NodeId.createMain(r.nextInt(10));
        final NodeId otherNode = NodeId.createMain(r.nextInt(10));

        final AtomicBoolean connected = new AtomicBoolean(true);
        final Socket socket = mock(Socket.class);
        doAnswer(i -> connected.get()).when(socket).isConnected();
        doAnswer(i -> connected.get()).when(socket).isBound();
        doAnswer(i -> !connected.get()).when(socket).isClosed();
        doAnswer(i -> {
                    connected.set(false);
                    return null;
                })
                .when(socket)
                .close();

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);
        out.writeSerializable(new BasicSoftwareVersion(1), true);
        out.writeInt(ByteConstants.COMM_CONNECT);
        out.close();

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(byteOut.toByteArray());
        doAnswer(i -> inputStream).when(socket).getInputStream();
        doAnswer(i -> mock(OutputStream.class)).when(socket).getOutputStream();
        final SocketFactory socketFactory = mock(SocketFactory.class);
        doAnswer(i -> socket).when(socketFactory).createClientSocket(any(), anyInt());

        final SettingsProvider settings = mock(SettingsProvider.class);
        doAnswer(i -> 100).when(settings).connectionStreamBufferSize();

        final OutboundConnectionCreator occ = new OutboundConnectionCreator(
                thisNode,
                settings,
                mock(ConnectionTracker.class),
                socketFactory,
                addressBook,
                true,
                new BasicSoftwareVersion(2));

        Connection connection = occ.createConnection(otherNode);

        assertTrue(connection instanceof NotConnectedConnection, "the connection should have failed");
    }

    @Test
    @DisplayName("Mismatched Version Ignored Test")
    void mismatchedVersionIgnoredTest() throws IOException, ConstructableRegistryException {

        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(BasicSoftwareVersion.class, BasicSoftwareVersion::new));

        final int numNodes = 10;
        final Random r = new Random();
        final AddressBook addressBook = new RandomAddressBookGenerator(r)
                .setSize(numNodes)
                .setWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                .setHashStrategy(RandomAddressBookGenerator.HashStrategy.FAKE_HASH)
                .setSequentialIds(true)
                .build();
        final NodeId thisNode = NodeId.createMain(r.nextInt(10));
        final NodeId otherNode = NodeId.createMain(r.nextInt(10));

        final AtomicBoolean connected = new AtomicBoolean(true);
        final Socket socket = mock(Socket.class);
        doAnswer(i -> connected.get()).when(socket).isConnected();
        doAnswer(i -> connected.get()).when(socket).isBound();
        doAnswer(i -> !connected.get()).when(socket).isClosed();
        doAnswer(i -> {
                    connected.set(false);
                    return null;
                })
                .when(socket)
                .close();

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);
        out.writeInt(ByteConstants.COMM_CONNECT);
        out.close();

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(byteOut.toByteArray());
        doAnswer(i -> inputStream).when(socket).getInputStream();
        doAnswer(i -> mock(OutputStream.class)).when(socket).getOutputStream();
        final SocketFactory socketFactory = mock(SocketFactory.class);
        doAnswer(i -> socket).when(socketFactory).createClientSocket(any(), anyInt());

        final SettingsProvider settings = mock(SettingsProvider.class);
        doAnswer(i -> 100).when(settings).connectionStreamBufferSize();

        final OutboundConnectionCreator occ = new OutboundConnectionCreator(
                thisNode,
                settings,
                mock(ConnectionTracker.class),
                socketFactory,
                addressBook,
                false,
                new BasicSoftwareVersion(2));

        Connection connection = occ.createConnection(otherNode);
        assertTrue(connection instanceof SocketConnection, "the returned connection should be a socket connection");
        assertEquals(thisNode, connection.getSelfId(), "self ID should match supplied ID");
        assertEquals(otherNode, connection.getOtherId(), "other ID should match supplied ID");
        assertTrue(connection.connected(), "a new connection should be connected");
        connection.disconnect();
        assertFalse(connection.connected(), "should not be connected after calling disconnect()");
    }
}
