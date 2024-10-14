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

package com.hedera.node.app.tss;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.tss.handlers.TssHandlers;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.Service;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.function.BiConsumer;

/**
 * Provides the network's threshold signature scheme (TSS) capability and, as a side effect, the ledger id, as this
 * is the exactly the same as the TSS public key.
 * <p>
 * The
 */
public interface TssBaseService extends Service {
    String NAME = "TssBaseService";

    /**
     * The current phase of the TSS service.
     */
    enum Phase {
        /**
         * The TSS service is starting up .
         */
        STARTUP,
        /**
         * The TSS service is creating initial key material (and hence the ledger id).
         */
        BOOTSTRAP,
        /**
         * The TSS service is ready to sign.
         */
        READY,
    }

    /**
     * Context for TSS operations.
     *
     * @param config the TSS configuration
     * @param selfAccountId the account ID of the node
     * @param consensusNow the current consensus time
     */
    record TssContext(@NonNull Configuration config, @NonNull AccountID selfAccountId, @NonNull Instant consensusNow) {
        public static TssContext from(@NonNull final HandleContext context) {
            requireNonNull(context);
            return new TssContext(
                    context.configuration(),
                    context.networkInfo().selfNodeInfo().accountId(),
                    context.consensusNow());
        }
    }

    @NonNull
    @Override
    default String getServiceName() {
        return NAME;
    }

    /**
     * Starts the process of keying a candidate roster with TSS key material.
     *
     * @param roster the candidate roster to key
     * @param context the TSS context
     */
    void startKeyingCandidate(@NonNull Roster roster, @NonNull TssContext context);

    /**
     * Requests a ledger signature on a message hash.  The ledger signature is computed asynchronously and returned
     * to all consumers that have been registered through {@link #registerLedgerSignatureConsumer}.
     *
     * @param messageHash The hash of the message to be signed by the ledger.
     */
    void requestLedgerSignature(@NonNull byte[] messageHash);

    /**
     * Registers a consumer of the message hash and the ledger signature on the message hash.
     *
     * @param consumer the consumer of ledger signatures and message hashes.
     */
    void registerLedgerSignatureConsumer(@NonNull BiConsumer<byte[], byte[]> consumer);

    /**
     * Unregisters a consumer of the message hash and the ledger signature on the message hash.
     *
     * @param consumer the consumer of ledger signatures and message hashes to unregister.
     */
    void unregisterLedgerSignatureConsumer(@NonNull BiConsumer<byte[], byte[]> consumer);

    /**
     * Returns the {@link TssHandlers} for this service.
     * @return the handlers
     */
    TssHandlers tssHandlers();
}
