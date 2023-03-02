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

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.merkle.utility.SerializableLong;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.platform.Connection;
import com.swirlds.platform.network.communication.handshake.HandshakeException;
import com.swirlds.platform.network.communication.handshake.VersionCompareHandshake;
import com.swirlds.platform.network.protocol.ProtocolRunnable;
import com.swirlds.platform.test.sync.ConnectionFactory;
import java.io.IOException;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class HandshakeTests {
    /**
     * Tests all scenarios that could occur during a version handshake
     */
    @Test
    void versionHandshakeTests() throws IOException, ConstructableRegistryException {
        final SoftwareVersion ourVersion = new BasicSoftwareVersion(5);
        final ProtocolRunnable handshakeThrows = new VersionCompareHandshake(ourVersion);
        final ProtocolRunnable handshakeLogs = new VersionCompareHandshake(ourVersion, false);
        final Pair<Connection, Connection> connections =
                ConnectionFactory.createLocalConnections(NodeId.createMain(0), NodeId.createMain(1));
        final Connection myConnection = connections.getLeft();
        final Connection theirConnection = connections.getRight();
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructable(new ClassConstructorPair(BasicSoftwareVersion.class, BasicSoftwareVersion::new));
        registry.registerConstructable(new ClassConstructorPair(SerializableLong.class, SerializableLong::new));

        // they have the same version as us
        clearWriteFlush(theirConnection, new BasicSoftwareVersion(5));
        Assertions.assertDoesNotThrow(() -> handshakeThrows.runProtocol(myConnection));
        clearWriteFlush(theirConnection, new BasicSoftwareVersion(5));
        Assertions.assertDoesNotThrow(() -> handshakeLogs.runProtocol(myConnection));

        // they have a different version
        clearWriteFlush(theirConnection, new BasicSoftwareVersion(6));
        Assertions.assertThrows(HandshakeException.class, () -> handshakeThrows.runProtocol(myConnection));
        clearWriteFlush(theirConnection, new BasicSoftwareVersion(6));
        Assertions.assertDoesNotThrow(() -> handshakeLogs.runProtocol(myConnection));

        // their version is null
        clearWriteFlush(theirConnection, null);
        Assertions.assertThrows(HandshakeException.class, () -> handshakeThrows.runProtocol(myConnection));
        clearWriteFlush(theirConnection, null);
        Assertions.assertDoesNotThrow(() -> handshakeLogs.runProtocol(myConnection));

        // their version is a different class
        clearWriteFlush(theirConnection, new SerializableLong(5));
        Assertions.assertThrows(HandshakeException.class, () -> handshakeThrows.runProtocol(myConnection));
        clearWriteFlush(theirConnection, new SerializableLong(5));
        Assertions.assertDoesNotThrow(() -> handshakeLogs.runProtocol(myConnection));
    }

    private void clearWriteFlush(final Connection connection, final SelfSerializable serializable) throws IOException {
        if (connection.getDis().available() > 0) {
            connection.getDis().readSerializable();
        }
        connection.getDos().writeSerializable(serializable, true);
        connection.getDos().flush();
    }
}
