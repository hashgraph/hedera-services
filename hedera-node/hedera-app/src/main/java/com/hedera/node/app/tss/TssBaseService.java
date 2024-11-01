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

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.tss.handlers.TssHandlers;
import com.hedera.node.app.tss.stores.ReadableTssStoreImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.merkle.Service;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Provides the network's threshold signature scheme (TSS) capability and, as a side effect, the ledger id, as this
 * is the exactly the same as the TSS public key.
 * <p>
 * The
 */
public interface TssBaseService extends Service {
    String NAME = "TssBaseService";

    /**
     * The status of the TSS service relative to a given roster and ledger id.
     */
    enum Status {
        /**
         * The service cannot yet recover the expected ledger id from its current key material for the roster.
         */
        PENDING_LEDGER_ID,
        /**
         * The TSS service is ready to sign.
         */
        READY,
    }

    @NonNull
    @Override
    default String getServiceName() {
        return NAME;
    }

    /**
     * @param metrics the metrics object being used to report tss performance
     */
    void registerMetrics(@NonNull TssMetrics metrics);

    /**
     * Returns the status of the TSS service relative to the given roster, ledger id, and given TSS base state.
     *
     * @param roster the candidate roster
     * @param ledgerId the expected ledger id
     * @param tssBaseStore the store to read the TSS base state from
     * @return the status of the TSS service
     */
    Status getStatus(@NonNull Roster roster, @NonNull Bytes ledgerId, @NonNull ReadableTssStoreImpl tssBaseStore);

    /**
     * Adopts the given roster for TSS operations.
     * @param roster the active roster
     * @throws IllegalArgumentException if the expected shares cannot be aggregated
     */
    void adopt(@NonNull Roster roster);

    /**
     * Bootstraps the TSS service for the given roster in the given context.
     * @param roster the network genesis roster
     * @param context the TSS context to use for bootstrapping
     * @param ledgerIdConsumer the consumer of the ledger id, to receive the ledger id as soon as it is available
     */
    void bootstrapLedgerId(
            @NonNull Roster roster, @NonNull HandleContext context, @NonNull Consumer<Bytes> ledgerIdConsumer);

    /**
     * Starts the process of keying a candidate roster with TSS key material.
     *
     * @param roster the candidate roster to key
     * @param context the TSS context
     */
    void setCandidateRoster(@NonNull Roster roster, @NonNull HandleContext context);

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
