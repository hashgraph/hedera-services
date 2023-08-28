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

package com.swirlds.platform.network.communication.handshake;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.logging.LogMarker;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.ProtocolRunnable;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Exchanges epoch hash with the peer, either throws a {@link HandshakeException} or logs an error if the hashes
 * do not match
 */
public class EpochHashCompareHandshake implements ProtocolRunnable {
    private static final Logger logger = LogManager.getLogger(EpochHashCompareHandshake.class);

    /**
     * The epoch hash in this node's state
     */
    private final Hash epochHash;

    /**
     * Whether an exception should be thrown if there is a mismatch during the handshake
     */
    private final boolean throwOnMismatch;

    /**
     * Constructor
     *
     * @param epochHash       the epoch hash in this node's state
     * @param throwOnMismatch if set to true, the protocol will throw an exception on a mismatch. if set to false, it will log an
     *                        error and continue
     */
    public EpochHashCompareHandshake(@Nullable final Hash epochHash, final boolean throwOnMismatch) {
        this.epochHash = epochHash;
        this.throwOnMismatch = throwOnMismatch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void runProtocol(@NonNull final Connection connection)
            throws NetworkProtocolException, IOException, InterruptedException {

        connection.getDos().writeSerializable(epochHash, true);
        connection.getDos().flush();

        final SelfSerializable readSerializable = connection.getDis().readSerializable();

        // if both are null, or both are hashes that match, then the protocol has succeeded
        if (epochHash == null && readSerializable == null
                || epochHash != null
                        && readSerializable instanceof Hash peerHash
                        && epochHash.compareTo(peerHash) == 0) {
            return;
        }

        final String message = String.format(
                "Incompatible epoch hash. Self epoch hash is '%s', peer epoch hash is '%s'",
                epochHash, readSerializable);

        if (throwOnMismatch) {
            throw new HandshakeException(message);
        } else {
            logger.error(LogMarker.ERROR.getMarker(), message);
        }
    }
}
