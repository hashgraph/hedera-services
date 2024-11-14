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

import static com.hedera.node.app.tss.handlers.TssUtils.getTssMessages;
import static com.hedera.node.app.tss.handlers.TssUtils.validateTssMessages;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.tss.cryptography.tss.api.TssLibrary;
import com.hedera.node.app.tss.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.cryptography.tss.api.TssParticipantPrivateInfo;
import com.hedera.node.app.tss.cryptography.tss.api.TssPrivateShare;
import com.hedera.node.app.tss.cryptography.tss.api.TssPublicShare;
import com.hedera.node.app.tss.stores.ReadableTssStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides access to the key material for the threshold signature scheme (TSS) for the active roster.
 */
@Singleton
public class TssKeysAccessor {
    private final TssLibrary tssLibrary;
    private TssKeysAccessor.TssKeys tssKeys;
    private final TssDirectoryAccessor tssDirectoryAccessor;

    @Inject
    public TssKeysAccessor(
            @NonNull final TssLibrary tssLibrary,
            @NonNull final TssDirectoryAccessor tssDirectoryAccessor) {
        this.tssLibrary = requireNonNull(tssLibrary);
        this.tssDirectoryAccessor = requireNonNull(tssDirectoryAccessor);
    }

    /**
     * Generates the key material for the active roster.
     *
     * @param state the state
     */
    public void generateKeyMaterialForActiveRoster(@NonNull final State state) {
        if (tssKeys != null) {
            return;
        }
        final var storeFactory = new ReadableStoreFactory(state);
        final var tssStore = storeFactory.getStore(ReadableTssStore.class);
        final var rosterStore = storeFactory.getStore(ReadableRosterStore.class);
        final var activeRosterHash = requireNonNull(rosterStore.getActiveRosterHash());
        final var activeParticipantDirectory = tssDirectoryAccessor.activeParticipantDirectory();
        final var tssMessageBodies = tssStore.getTssMessageBodies(activeRosterHash);
        final var validTssMessages = getTssMessages(tssMessageBodies);
        final var activeRosterShares = getTssPrivateShares(activeParticipantDirectory, tssStore, activeRosterHash);
        final var activeRosterPublicShares =
                tssLibrary.rekeyStage().shareExtractor(activeParticipantDirectory, validTssMessages)
                        .allPublicShares();
        final var totalShares = activeParticipantDirectory.getTotalShares();
        this.tssKeys = new TssKeysAccessor.TssKeys(
                activeRosterShares,
                activeRosterPublicShares,
                activeRosterHash,
                activeParticipantDirectory,
                totalShares);
    }

    @NonNull
    private List<TssPrivateShare> getTssPrivateShares(
            @NonNull final TssParticipantDirectory activeRosterParticipantDirectory,
            @NonNull final ReadableTssStore tssStore,
            @NonNull final Bytes activeRosterHash) {
        final var validTssOps = validateTssMessages(
                tssStore.getTssMessageBodies(activeRosterHash), activeRosterParticipantDirectory, tssLibrary);
        final var validTssMessages = getTssMessages(validTssOps);
        // FUTURE: Use node real tssDecryptPrivateKey
        final var participantPrivateInfo = new TssParticipantPrivateInfo()
        return tssLibrary.rekeyStage()
                .shareExtractor(activeRosterParticipantDirectory, validTssMessages)
                .ownedPrivateShares(TssParticipantPrivateInfo);
    }

    /**
     * Returns the TSS key material for the active roster.
     *
     * @return the TSS key material for the active roster
     */
    public TssKeysAccessor.TssKeys accessTssKeys() {
        return tssKeys;
    }

    /**
     * Represents the TSS key material for the active roster.
     *
     * @param activeRosterShares         the active roster private shares
     * @param activeRosterPublicShares   the active roster public shares
     * @param activeRosterHash           the active roster hash
     * @param activeParticipantDirectory the active participant directory
     * @param totalShares                the total number of shares
     */
    public record TssKeys(
            @NonNull List<TssPrivateShare> activeRosterShares,
            @NonNull List<TssPublicShare> activeRosterPublicShares,
            @NonNull Bytes activeRosterHash,
            @NonNull TssParticipantDirectory activeParticipantDirectory,
            long totalShares) {}

    @VisibleForTesting
    void setTssKeys(@NonNull final TssKeys tssKeys) {
        this.tssKeys = tssKeys;
    }
}
