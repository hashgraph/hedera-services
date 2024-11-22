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
import com.hedera.node.app.roster.RosterService;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.tss.api.TssMessage;
import com.hedera.node.app.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.handlers.TssHandlers;
import com.hedera.node.app.tss.stores.ReadableTssStoreImpl;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.hedera.node.app.workflows.handle.steps.UserTxn;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.Service;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Provides the network's threshold signature scheme (TSS) capability and,
 * as a side effect, the ledger id; as this is exactly the same as the TSS
 * public key.
 */
public interface TssBaseService extends Service {
    /**
     * Since the roster service has to decide to adopt the candidate roster
     * based on the available key material, the TSS service must be migrated
     * before the roster service.
     */
    int MIGRATION_ORDER = RosterService.MIGRATION_ORDER - 1;

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
     * Returns the status of the TSS service relative to the given roster, ledger id, and given TSS base state.
     *
     * @param roster       the candidate roster
     * @param ledgerId     the expected ledger id
     * @param tssBaseStore the store to read the TSS base state from
     * @return the status of the TSS service
     */
    Status getStatus(@NonNull Roster roster, @NonNull Bytes ledgerId, @NonNull ReadableTssStoreImpl tssBaseStore);

    /**
     * Bootstraps the TSS service for the given roster in the given context.
     *
     * @param roster           the network genesis roster
     * @param context          the TSS context to use for bootstrapping
     * @param ledgerIdConsumer the consumer of the ledger id, to receive the ledger id as soon as it is available
     */
    void bootstrapLedgerId(
            @NonNull Roster roster, @NonNull HandleContext context, @NonNull Consumer<Bytes> ledgerIdConsumer);

    /**
     * Starts the process of keying a candidate roster with TSS key material.
     *
     * @param roster  the candidate roster to key
     * @param context the TSS context
     */
    void setCandidateRoster(@NonNull Roster roster, @NonNull HandleContext context);

    /**
     * Requests a ledger signature on a message hash.  The ledger signature is computed asynchronously and returned
     * to all consumers that have been registered through {@link #registerLedgerSignatureConsumer}.
     *
     * @param messageHash           The hash of the message to be signed by the ledger.
     * @param lastUsedConsensusTime The last used consensus time in the round.
     */
    void requestLedgerSignature(@NonNull byte[] messageHash, @NonNull Instant lastUsedConsensusTime);

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
     *
     * @return the handlers
     */
    TssHandlers tssHandlers();

    /**
     * Returns the active roster for the given network state.
     * If the network is not at genesis and is an upgrade, then the active roster is determined based on the
     * votes in the state. If the network is at genesis, then the active roster is the genesis roster.
     *
     * @param state           the network state
     * @param trigger         the initialization trigger
     * @param serviceMigrator the service migrator
     * @param version         the services software version
     * @param configuration   the configuration
     * @return the active roster
     */
    Roster chooseRosterForNetwork(
            @NonNull State state,
            @NonNull InitTrigger trigger,
            @NonNull ServiceMigrator serviceMigrator,
            @NonNull ServicesSoftwareVersion version,
            @NonNull Configuration configuration,
            @NonNull Roster overrideRoster);

    /**
     * Regenerates the key material for the active roster. This happens each time the active roster is updated.
     * This is only called when the tss key candidate roster feature flag is enabled.
     *
     * @param state the network state
     */
    void regenerateKeyMaterial(@NonNull State state);

    /**
     * Generates the participant directory for the active roster.
     * @param state the network state
     */
    void generateParticipantDirectory(@NonNull State state);

    /**
     * Returns the ledger id from the given TSS participant directory and TSS messages.
     * @param directory the participant directory
     * @param tssMessages the TSS messages
     * @return the ledger id
     */
    Bytes ledgerIdFrom(@NonNull TssParticipantDirectory directory, @NonNull List<TssMessage> tssMessages);

    /**
     * Processes the TSS encryption key checks for the given state and consensus time from the user transaction.
     *
     * @param userTxn       the user transaction
     * @param handleContext the handle context
     * @param keysAndCerts  the keys and certificates
     */
    void processTssEncryptionKeyChecks(@NonNull final UserTxn userTxn, @NonNull final HandleContext handleContext, @NonNull final
    KeysAndCerts keysAndCerts);
}
