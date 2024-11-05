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

import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.api.TssPrivateShare;
import com.hedera.node.app.tss.stores.ReadableTssStore;
import com.hedera.node.config.data.TssConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.state.State;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PrivateKeysAccessor {
    private Map<Bytes, List<TssPrivateShare>> privateShares;
    private List<TssPrivateShare> activeRosterShares;
    private Bytes activeRosterHash;

    private final TssLibrary tssLibrary;

    @Inject
    public PrivateKeysAccessor(@NonNull final TssLibrary tssLibrary) {
        this.tssLibrary = requireNonNull(tssLibrary);
    }

    public void generateKeyMaterialForActiveRoster(
            @NonNull final State state, @NonNull final Configuration configuration, @NonNull final NodeInfo selfId) {
        final var storeFactory = new ReadableStoreFactory(state);
        final var tssStore = storeFactory.getStore(ReadableTssStore.class);
        final var rosterStore = storeFactory.getStore(ReadableRosterStore.class);

        final var maxSharesPerNode =
                configuration.getConfigData(TssConfig.class).maxSharesPerNode();
        this.activeRosterHash = rosterStore.getActiveRosterHash();
        final var activeRoster =
                requireNonNull(storeFactory.getStore(ReadableRosterStore.class).getActiveRoster());

        final var activeDirectory = computeParticipantDirectory(activeRoster, maxSharesPerNode, (int) selfId.nodeId());
        final var activeRosterHash = RosterUtils.hash(activeRoster).getBytes();
        final var tssShares = getTssPrivateShares(activeDirectory, tssStore, activeRosterHash);
        privateShares.put(activeRosterHash, tssShares);
        activeRosterShares = tssShares;
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

    public void reset() {
        privateShares = new LinkedHashMap<>();
    }

    public List<TssPrivateShare> getPrivateShares(@NonNull final Bytes activeRosterHash) {
        return privateShares.get(activeRosterHash);
    }

    public List<TssPrivateShare> getActiveRosterShares() {
        return activeRosterShares;
    }

    public Bytes getActiveRosterHash() {
        return activeRosterHash;
    }
}
