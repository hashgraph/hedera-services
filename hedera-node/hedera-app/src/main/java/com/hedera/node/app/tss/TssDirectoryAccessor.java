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
import static java.util.Objects.requireNonNull;

import com.hedera.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.config.data.TssConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides access to the {@link TssParticipantDirectory} for the active roster.
 */
@Singleton
public class TssDirectoryAccessor {
    private TssParticipantDirectory tssParticipantDirectory;
    private final Supplier<Configuration> configurationSupplier;
    private final Supplier<NodeInfo> nodeInfoSupplier;

    @Inject
    public TssDirectoryAccessor(@NonNull final AppContext appContext) {
        this.configurationSupplier = appContext.configSupplier();
        this.nodeInfoSupplier = appContext.selfNodeInfoSupplier();
    }

    /**
     * Generates the participant directory for the active roster.
     *
     * @param state   state
     */
    public void generateTssParticipantDirectory(@NonNull final State state) {
        if (tssParticipantDirectory != null) {
            return;
        }
        final var maxSharesPerNode =
                configurationSupplier.get().getConfigData(TssConfig.class).maxSharesPerNode();
        final var readableStoreFactory = new ReadableStoreFactory(state);
        final var rosterStore = readableStoreFactory.getStore(ReadableRosterStore.class);
        final var activeRoster = requireNonNull(rosterStore.getActiveRoster());
        this.tssParticipantDirectory = computeParticipantDirectory(
                activeRoster, maxSharesPerNode, (int) nodeInfoSupplier.get().nodeId());
    }

    public TssParticipantDirectory activeParticipantDirectory() {
        return tssParticipantDirectory;
    }
}
