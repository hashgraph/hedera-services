/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test;

import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.swirlds.common.io.exceptions.BadIOException;
import com.swirlds.common.io.extendable.extensions.CountingStreamExtension;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.SocketConnection;
import com.swirlds.platform.SwirldsPlatform;
import com.swirlds.platform.sync.SyncInputStream;
import com.swirlds.platform.sync.SyncOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SocketConnectionTests {

    private NodeId selfId;
    private NodeId otherId;
    private SwirldsPlatform platform;
    private Socket socket;
    private SyncInputStream dis;
    private SyncOutputStream dos;
    private SocketConnection conn;

    private static Stream<Arguments> booleans() {
        return Stream.of(Arguments.of(true), Arguments.of(false));
    }

    private void initConnection(final boolean isOutbound) {
        conn = SocketConnection.create(selfId, otherId, platform, isOutbound, socket, dis, dos);
    }

    @BeforeEach
    void newMocks() {
        selfId = mock(NodeId.class);
        otherId = mock(NodeId.class);
        platform = mock(SwirldsPlatform.class, withSettings().withoutAnnotations());
        socket = mock(Socket.class);
        dis = mock(SyncInputStream.class);
        dos = mock(SyncOutputStream.class);
    }

    @ParameterizedTest
    @MethodSource("booleans")
    void testConstructor(final boolean outbound) {
        initConnection(outbound);

        assertEquals(selfId, conn.getSelfId(), "'selfId' should be initialized to the provided value.");
        assertEquals(otherId, conn.getOtherId(), "'otherId' should be initialized to the provided value.");
        assertEquals(outbound, conn.isOutbound(), "'outbound' should be initialized to the provided value.");
        assertEquals(socket, conn.getSocket(), "'socket' should be initialized to the provided value.");
        assertEquals(dis, conn.getDis(), "'dis' should be initialized to the provided value.");
        assertEquals(dos, conn.getDos(), "'dos' should be initialized to the provided value.");
    }

    @ParameterizedTest
    @MethodSource("booleans")
    @Tag(TIME_CONSUMING)
    void testSingleDisconnect(final boolean outbound) throws IOException {
        initConnection(outbound);

        conn.disconnect();

        verifyClosed(conn, 1);
    }

    @ParameterizedTest
    @MethodSource("booleans")
    void testMultipleDisconnects(final boolean outbound) throws IOException {
        initConnection(outbound);

        conn.disconnect();
        conn.disconnect();

        verifyClosed(conn, 2);
    }

    @ParameterizedTest
    @MethodSource("booleans")
    void testCloseDoesNotThrow(final boolean outbound) throws IOException {
        doThrow(IOException.class).when(socket).close();
        initConnection(outbound);
        assertDoesNotThrow(() -> conn.disconnect(), "close() should not throw any exceptions.");

        doThrow(IOException.class).when(dis).close();
        initConnection(outbound);
        assertDoesNotThrow(() -> conn.disconnect(), "close() should not throw any exceptions.");

        doThrow(IOException.class).when(dos).close();
        initConnection(outbound);
        assertDoesNotThrow(() -> conn.disconnect(), "close() should not throw any exceptions.");
    }

    private void verifyClosed(final SocketConnection conn, final int numCloses) throws IOException {
        verify(socket, times(numCloses)).close();
        verify(dis, times(numCloses)).close();
        verify(dos, times(numCloses)).close();

        assertNotNull(conn.getSocket(), "socket should not be nulled on disconnect()");
        assertNotNull(conn.getDis(), "dis should not be nulled on disconnect()");
        assertNotNull(conn.getDos(), "dos should not be nulled on disconnect()");
    }

    @ParameterizedTest
    @MethodSource("booleans")
    void testConnected_SocketNotConnected(final boolean outbound) {
        when(socket.isClosed()).thenReturn(false);
        when(socket.isBound()).thenReturn(true);
        when(socket.isConnected()).thenReturn(false);

        initConnection(outbound);

        assertFalse(conn.connected(), "connected() should return false when socket is not connected.");
    }

    @ParameterizedTest
    @MethodSource("booleans")
    void testConnected_SocketNotBound(final boolean outbound) {
        when(socket.isClosed()).thenReturn(false);
        when(socket.isBound()).thenReturn(false);
        when(socket.isConnected()).thenReturn(true);

        initConnection(outbound);

        assertFalse(conn.connected(), "connected() should return false when the socket is not bound.");
    }

    @ParameterizedTest
    @MethodSource("booleans")
    void testConnected_SocketClosed(final boolean outbound) {
        when(socket.isClosed()).thenReturn(true);
        when(socket.isBound()).thenReturn(true);
        when(socket.isConnected()).thenReturn(true);

        initConnection(outbound);

        assertFalse(conn.connected(), "connected() should return false when the socket is closed.");
    }

    @ParameterizedTest
    @MethodSource("booleans")
    void testConnected(final boolean outbound) {
        when(socket.isClosed()).thenReturn(false);
        when(socket.isBound()).thenReturn(true);
        when(socket.isConnected()).thenReturn(true);

        initConnection(outbound);

        assertTrue(conn.connected(), "connected() should return true.");
    }

    @ParameterizedTest
    @MethodSource("booleans")
    void testConnected_SocketThrows(final boolean outbound) {
        when(socket.isClosed()).thenReturn(false);
        when(socket.isBound()).thenReturn(true);
        when(socket.isConnected()).thenThrow(RuntimeException.class);

        initConnection(outbound);

        boolean isConnected = assertDoesNotThrow(conn::connected, "connected() should not thrown any exceptions.");
        assertFalse(isConnected, "connected() should return false when an exception occurs.");
    }

    @ParameterizedTest
    @MethodSource("booleans")
    void testInitForSync_NotConnected(final boolean outbound) {
        when(socket.isClosed()).thenReturn(true);
        initConnection(outbound);
        assertThrows(BadIOException.class, conn::initForSync, "Expected an exception when the socket is closed.");
    }

    @ParameterizedTest
    @MethodSource("booleans")
    void testNullSocket(final boolean outbound) {
        assertThrows(
                Exception.class,
                () -> SocketConnection.create(selfId, otherId, platform, outbound, null, dis, dos),
                "Expected an exception when a null socket is provided.");
    }

    @ParameterizedTest
    @MethodSource("booleans")
    void testNullDis(final boolean outbound) {
        assertThrows(
                Exception.class,
                () -> SocketConnection.create(selfId, otherId, platform, outbound, socket, null, dos),
                "Expected an exception when a null dis is provided.");
    }

    @ParameterizedTest
    @MethodSource("booleans")
    void testNullDos(final boolean outbound) {
        assertThrows(
                Exception.class,
                () -> SocketConnection.create(selfId, otherId, platform, outbound, socket, dis, null),
                "Expected an exception when a null dos is provided.");
    }

    @ParameterizedTest
    @MethodSource("booleans")
    void testInitForSync(final boolean outbound) throws SocketException {
        CountingStreamExtension disCounter = mock(CountingStreamExtension.class);
        CountingStreamExtension dosCounter = mock(CountingStreamExtension.class);
        when(dis.getSyncByteCounter()).thenReturn(disCounter);
        when(dos.getSyncByteCounter()).thenReturn(dosCounter);

        // mock Connection.connected() = true
        when(socket.isClosed()).thenReturn(false);
        when(socket.isBound()).thenReturn(true);
        when(socket.isConnected()).thenReturn(true);

        initConnection(outbound);
        assertDoesNotThrow(() -> conn.initForSync(), "An exception should not be thrown when the connection is valid.");

        verify(dis).getSyncByteCounter();
        verify(dos).getSyncByteCounter();
        verify(disCounter).resetCount();
        verify(dosCounter).resetCount();
        verify(socket).setSoTimeout(anyInt());
    }

    @ParameterizedTest
    @MethodSource("booleans")
    void testDescription(final boolean outbound) {
        initConnection(outbound);

        assertTrue(conn.getDescription().contains(selfId.toString()), "Description should contain the self id.");
        assertTrue(conn.getDescription().contains(otherId.toString()), "Description should contain the other id.");
        assertTrue(
                conn.getDescription().contains(outbound ? "->" : "<-"),
                String.format("Incorrect arrow direction for %s connection", outbound ? "outbound" : "inbound"));
    }
}
