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

package com.swirlds.platform.test.network.communication.handshake;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.platform.network.Connection;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;

/**
 * Utilities for network tests
 */
public class HandshakeTestUtils {
    /**
     * Hidden constructor
     */
    private HandshakeTestUtils() {}

    /**
     * Reads a serializable from a connection if available, writes a serializable to a connection, and then flushes.
     *
     * @param connection   the connection to read from and write to
     * @param serializable the serializable to write
     * @throws IOException if an IO error occurs
     */
    public static void clearWriteFlush(
            @NonNull final Connection connection, @Nullable final SelfSerializable serializable) throws IOException {
        if (connection.getDis().available() > 0) {
            connection.getDis().readSerializable();
        }
        connection.getDos().writeSerializable(serializable, true);
        connection.getDos().flush();
    }
}
