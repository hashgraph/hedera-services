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

import static com.hedera.node.app.tss.handlers.TssUtils.computeParticipantDirectory;
import static com.hedera.node.app.tss.handlers.TssUtils.getTssMessages;
import static com.hedera.node.app.tss.handlers.TssUtils.validateTssMessages;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.api.TssPrivateShare;
import com.hedera.node.app.tss.api.TssPublicShare;
import com.hedera.node.app.tss.stores.ReadableTssStore;
import com.hedera.node.config.data.TssConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TssKeyMaterialAccessor {
    private List<TssPrivateShare> activeRosterShares;
    private List<TssPublicShare> activeRosterPublicShares;
    private Bytes activeRosterHash;
    private final TssLibrary tssLibrary;
    private TssParticipantDirectory activeParticipantDirectory;

    @Inject
    public TssKeyMaterialAccessor(@NonNull final TssLibrary tssLibrary) {
        this.tssLibrary = requireNonNull(tssLibrary);
    }

    /**
     * Generates the key material for the active roster.
     * @param state the state
     * @param configuration the configuration
     * @param selfId the node id
     * @param rosterStore the roster store
     */
    public void generateKeyMaterialForActiveRoster(
            @NonNull final State state,
            @NonNull final Configuration configuration,
            final long selfId,
            @NonNull final ReadableRosterStore rosterStore) {
        final var storeFactory = new ReadableStoreFactory(state);
        final var tssStore = storeFactory.getStore(ReadableTssStore.class);
        final var maxSharesPerNode =
                configuration.getConfigData(TssConfig.class).maxSharesPerNode();
        this.activeRosterHash = requireNonNull(rosterStore.getActiveRosterHash());
        final var activeRoster = requireNonNull(rosterStore.getActiveRoster());
        this.activeParticipantDirectory = computeParticipantDirectory(activeRoster, maxSharesPerNode, (int) selfId);
        this.activeRosterShares = getTssPrivateShares(activeParticipantDirectory, tssStore, activeRosterHash);

        final var tssMessageBodies = tssStore.getTssMessageBodies(activeRosterHash);
        final var validTssMessages = getTssMessages(tssMessageBodies);
        this.activeRosterPublicShares = tssLibrary.computePublicShares(activeParticipantDirectory, validTssMessages);
    }

    public TssParticipantDirectory candidateRosterParticipantDirectory(
            @NonNull final Roster candidateRoster, final long maxSharesPerNode, final int selfId) {
        return computeParticipantDirectory(candidateRoster, maxSharesPerNode, selfId);
    }

    @NonNull
    private List<TssPrivateShare> getTssPrivateShares(
            @NonNull final TssParticipantDirectory activeRosterParticipantDirectory,
            @NonNull final ReadableTssStore tssStore,
            @NonNull final Bytes activeRosterHash) {
        final var validTssOps = validateTssMessages(
                tssStore.getTssMessageBodies(activeRosterHash), activeRosterParticipantDirectory, tssLibrary);
        final var validTssMessages = getTssMessages(validTssOps);
        return tssLibrary.decryptPrivateShares(activeRosterParticipantDirectory, validTssMessages);
    }

    /**
     * Resets the key material.
     */
    public void reset() {
        if (activeRosterShares != null) {
            activeRosterShares.clear();
        }
        if (activeRosterPublicShares != null) {
            activeRosterPublicShares.clear();
        }
        activeRosterHash = Bytes.EMPTY;
        activeParticipantDirectory = null;
    }

    /**
     * Returns the active roster public shares.
     * @return the active roster public shares
     */
    public List<TssPublicShare> activeRosterPublicShares() {
        return requireNonNull(activeRosterPublicShares);
    }

    /**
     * Returns the active roster participant directory.
     * @return the active roster participant directory
     */
    public TssParticipantDirectory activeRosterParticipantDirectory() {
        return requireNonNull(activeParticipantDirectory);
    }

    /**
     * Returns the active roster hash.
     * @return the active roster hash
     */
    public Bytes activeRosterHash() {
        return requireNonNull(activeRosterHash);
    }

    /**
     * Returns the active roster shares.
     * @return the active roster shares
     */
    public List<TssPrivateShare> activeRosterShares() {
        return requireNonNull(activeRosterShares);
    }
}
