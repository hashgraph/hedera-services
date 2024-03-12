/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.ProtocolRunnable;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Exchanges a hash with the peer, either throws a {@link HandshakeException} or logs an error if the hashes
 * do not match
 */
public class HashCompareHandshake implements ProtocolRunnable {
    private static final Logger logger = LogManager.getLogger(HashCompareHandshake.class);

    /**
     * This node's hash
     */
    private final Hash hash;

    /**
     * Whether an exception should be thrown if there is a mismatch during the handshake
     */
    private final boolean throwOnMismatch;

    /**
     * Constructor
     *
     * @param hash            this node's hash
     * @param throwOnMismatch if set to true, the protocol will throw an exception on a mismatch.
     *                        if set to false, it will log an error and continue
     */
    public HashCompareHandshake(@Nullable final Hash hash, final boolean throwOnMismatch) {
        this.hash = hash;
        this.throwOnMismatch = throwOnMismatch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void runProtocol(@NonNull final Connection connection)
            throws NetworkProtocolException, IOException, InterruptedException {

        connection.getDos().writeSerializable(hash, false);
        connection.getDos().flush();

        final SelfSerializable readSerializable = connection.getDis().readSerializable(false, Hash::new);

        if (Objects.equals(hash, readSerializable)) {
            return;
        }

        final String message =
                String.format("Incompatible hash. Self hash is '%s', peer hash is '%s'", hash, readSerializable);

        if (throwOnMismatch) {
            throw new HandshakeException(message);
        } else {
            logger.error(EXCEPTION.getMarker(), message);
        }
    }
}
