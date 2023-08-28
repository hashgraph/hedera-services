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

package com.swirlds.platform.test.network.communication.handshake;

import static com.swirlds.platform.test.network.communication.handshake.HandshakeTestUtils.clearWriteFlush;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.base.utility.Pair;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.utility.SerializableLong;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.communication.handshake.EpochHashCompareHandshake;
import com.swirlds.platform.network.communication.handshake.HandshakeException;
import com.swirlds.platform.network.communication.handshake.VersionCompareHandshake;
import com.swirlds.platform.network.protocol.ProtocolRunnable;
import com.swirlds.platform.test.sync.ConnectionFactory;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link VersionCompareHandshake}
 */
class EpochHashHandshakeTests {
    private Connection theirConnection;
    private Connection myConnection;

    @BeforeEach
    void setup() throws ConstructableRegistryException, IOException {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructable(new ClassConstructorPair(Hash.class, Hash::new));
        registry.registerConstructable(new ClassConstructorPair(SerializableLong.class, SerializableLong::new));

        final Pair<Connection, Connection> connections =
                ConnectionFactory.createLocalConnections(new NodeId(0L), new NodeId(1));
        myConnection = connections.left();
        theirConnection = connections.right();
    }

    @Test
    @DisplayName("They have the same epoch hash as us")
    void sameHash() throws IOException {
        final Hash commonHash = RandomUtils.randomHash();

        final ProtocolRunnable protocolToleratingMismatch = new EpochHashCompareHandshake(commonHash, false);
        final ProtocolRunnable protocolThrowingOnMismatch = new EpochHashCompareHandshake(commonHash, true);

        clearWriteFlush(theirConnection, commonHash);
        assertDoesNotThrow(() -> protocolThrowingOnMismatch.runProtocol(myConnection));

        clearWriteFlush(theirConnection, commonHash);
        assertDoesNotThrow(() -> protocolToleratingMismatch.runProtocol(myConnection));
    }

    @Test
    @DisplayName("They have a different epoch hash than us")
    void differentHash() throws IOException {
        final Hash ourHash = RandomUtils.randomHash();
        final Hash theirHash = RandomUtils.randomHash();

        final ProtocolRunnable protocolToleratingMismatch = new EpochHashCompareHandshake(ourHash, false);
        final ProtocolRunnable protocolThrowingOnMismatch = new EpochHashCompareHandshake(ourHash, true);

        clearWriteFlush(theirConnection, theirHash);
        assertThrows(HandshakeException.class, () -> protocolThrowingOnMismatch.runProtocol(myConnection));

        clearWriteFlush(theirConnection, theirHash);
        assertDoesNotThrow(() -> protocolToleratingMismatch.runProtocol(myConnection));
    }

    @Test
    @DisplayName("Their epoch hash is null, and ours isn't")
    void theirHashIsNull() throws IOException {
        final Hash ourHash = RandomUtils.randomHash();

        final ProtocolRunnable protocolToleratingMismatch = new EpochHashCompareHandshake(ourHash, false);
        final ProtocolRunnable protocolThrowingOnMismatch = new EpochHashCompareHandshake(ourHash, true);

        clearWriteFlush(theirConnection, null);
        assertThrows(HandshakeException.class, () -> protocolThrowingOnMismatch.runProtocol(myConnection));

        clearWriteFlush(theirConnection, null);
        assertDoesNotThrow(() -> protocolToleratingMismatch.runProtocol(myConnection));
    }

    @Test
    @DisplayName("Our epoch hash is null, and theirs isn't")
    void ourHashIsNull() throws IOException {
        final Hash theirHash = RandomUtils.randomHash();

        final ProtocolRunnable protocolToleratingMismatch = new EpochHashCompareHandshake(null, false);
        final ProtocolRunnable protocolThrowingOnMismatch = new EpochHashCompareHandshake(null, true);

        clearWriteFlush(theirConnection, theirHash);
        assertThrows(HandshakeException.class, () -> protocolThrowingOnMismatch.runProtocol(myConnection));

        clearWriteFlush(theirConnection, theirHash);
        assertDoesNotThrow(() -> protocolToleratingMismatch.runProtocol(myConnection));
    }

    @Test
    @DisplayName("Both epoch hashes are null")
    void bothHashesAreNull() throws IOException {
        final ProtocolRunnable protocolToleratingMismatch = new EpochHashCompareHandshake(null, false);
        final ProtocolRunnable protocolThrowingOnMismatch = new EpochHashCompareHandshake(null, true);

        clearWriteFlush(theirConnection, null);
        assertDoesNotThrow(() -> protocolThrowingOnMismatch.runProtocol(myConnection));

        clearWriteFlush(theirConnection, null);
        assertDoesNotThrow(() -> protocolToleratingMismatch.runProtocol(myConnection));
    }

    @Test
    @DisplayName("Their epoch hash is a different class")
    void differentClass() throws IOException {
        final Hash ourHash = RandomUtils.randomHash();
        final SerializableLong theirHash = new SerializableLong(5);

        final ProtocolRunnable protocolToleratingMismatch = new EpochHashCompareHandshake(ourHash, false);
        final ProtocolRunnable protocolThrowingOnMismatch = new EpochHashCompareHandshake(ourHash, true);

        clearWriteFlush(theirConnection, theirHash);
        assertThrows(HandshakeException.class, () -> protocolThrowingOnMismatch.runProtocol(myConnection));

        clearWriteFlush(theirConnection, theirHash);
        assertDoesNotThrow(() -> protocolToleratingMismatch.runProtocol(myConnection));
    }
}
