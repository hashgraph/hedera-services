/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.record;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.state.FilteredWritableStates;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.workflows.record.GenesisRecordsBuilder;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * An implementation of {@link MigrationContext}.
 *
 * @param previousStates        The previous states.
 * @param newStates             The new states, preloaded with any new state definitions.
 * @param configuration         The configuration to use
 * @param genesisRecordsBuilder The instance responsible for genesis records
 * @param writableEntityIdStore The instance responsible for generating new entity IDs (ONLY during
 *                              migrations). Note that this is nullable only because it cannot exist
 *                              when the entity ID service itself is being migrated
 * @param previousVersion
 */
public record MigrationContextImpl(
        @NonNull ReadableStates previousStates,
        @NonNull WritableStates newStates,
        @NonNull Configuration configuration,
        @NonNull NetworkInfo networkInfo,
        @NonNull GenesisRecordsBuilder genesisRecordsBuilder,
        @Nullable WritableEntityIdStore writableEntityIdStore,
        @Nullable SemanticVersion previousVersion)
        implements MigrationContext {

    public MigrationContextImpl {
        requireNonNull(previousStates);
        requireNonNull(newStates);
        requireNonNull(configuration);
        requireNonNull(networkInfo);
        requireNonNull(genesisRecordsBuilder);
    }

    @Override
    public long newEntityNum() {
        return requireNonNull(writableEntityIdStore, "Entity ID store needs to exist first")
                .incrementAndGet();
    }

    @Override
    public void copyAndReleaseOnDiskState(@NonNull final String stateKey) {
        requireNonNull(stateKey);
        if (newStates instanceof FilteredWritableStates filteredWritableStates
                && filteredWritableStates.getDelegate()
                        instanceof MerkleHederaState.MerkleWritableStates merkleWritableStates) {
            merkleWritableStates.copyAndReleaseVirtualMap(stateKey);
        } else {
            throw new UnsupportedOperationException("On-disk state is inaccessible");
        }
    }
}
