/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.platform.Connection;
import com.swirlds.platform.network.connection.NotConnectedConnection;
import org.junit.jupiter.api.Test;

class ConnectionTest {

    public static final String THROW_MESSAGE = "should throw when calling this method";

    @Test
    void notConnectedConnectionTest() {
        Connection c = NotConnectedConnection.getSingleton();
        assertDoesNotThrow(c::disconnect, "disconnect should do nothing");
        assertTrue(
                c.getDescription().toLowerCase().contains("notconnected"),
                "description should indicate it is not connected");
        assertFalse(c.connected(), "should never be connected");
        assertThrows(Exception.class, c::getSelfId, THROW_MESSAGE);
        assertThrows(Exception.class, c::getOtherId, THROW_MESSAGE);
        assertThrows(Exception.class, c::getDis, THROW_MESSAGE);
        assertThrows(Exception.class, c::getDos, THROW_MESSAGE);
        assertThrows(Exception.class, c::initForSync, THROW_MESSAGE);
        assertThrows(Exception.class, c::isOutbound, THROW_MESSAGE);
        assertThrows(Exception.class, c::getTimeout, THROW_MESSAGE);
        assertThrows(Exception.class, () -> c.setTimeout(0), THROW_MESSAGE);
    }
}
