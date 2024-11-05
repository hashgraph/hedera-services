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

package com.swirlds.platform.util;

import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.MerkleStateLifecycles;
import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.platform.state.service.PlatformStateService;
import com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema;
import com.swirlds.platform.system.*;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.state.State;
import com.swirlds.state.merkle.StateMetadata;
import com.swirlds.state.merkle.singleton.SingletonNode;
import com.swirlds.state.spi.*;
import com.swirlds.state.spi.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.MarkerManager;

public enum NoOpMerkleStateLifecycles implements MerkleStateLifecycles {
    NO_OP_MERKLE_STATE_LIFECYCLES;

    private static final Logger logger = LogManager.getLogger(NoOpMerkleStateLifecycles.class);

    public List<StateChanges.Builder> initPlatformState(@NonNull final State state) {
        if (!(state instanceof MerkleStateRoot merkleStateRoot)) {
            throw new IllegalArgumentException("Can only be used with MerkleStateRoot instances");
        }
        final var schema = new V0540PlatformStateSchema();
        schema.statesToCreate().stream()
                .sorted(Comparator.comparing(StateDefinition::stateKey))
                .forEach(def -> {
                    final var md = new StateMetadata<>(PlatformStateService.NAME, schema, def);
                    if (def.singleton()) {
                        merkleStateRoot.putServiceStateIfAbsent(
                                md,
                                () -> new SingletonNode<>(
                                        md.serviceName(),
                                        md.stateDefinition().stateKey(),
                                        md.singletonClassId(),
                                        md.stateDefinition().valueCodec(),
                                        null));
                    } else {
                        throw new IllegalStateException("PlatformStateService only expected to use singleton states");
                    }
                });
        final var writableStates = state.getWritableStates(PlatformStateService.NAME);
        final var migrationContext = new MigrationContext() {
            @NonNull
            @Override
            public ReadableStates previousStates() {
                return null;
            }

            @NonNull
            @Override
            public WritableStates newStates() {
                return writableStates;
            }

            @NonNull
            @Override
            public Configuration configuration() {
                return null;
            }

            @Nullable
            @Override
            public NetworkInfo genesisNetworkInfo() {
                return null;
            }

            @Override
            public long newEntityNum() {
                return 0;
            }

            @Override
            public void copyAndReleaseOnDiskState(String stateKey) {
                // no-op
            }

            @Nullable
            @Override
            public SemanticVersion previousVersion() {
                return null;
            }

            @Override
            public Map<String, Object> sharedValues() {
                return Map.of();
            }
        };

        schema.migrate(migrationContext);
        ((CommittableWritableStates) writableStates).commit();
        return Collections.emptyList();
    }

    @Override
    public void onPreHandle(@NonNull Event event, @NonNull State state) {
        // no-op
    }

    @Override
    public void onHandleConsensusRound(@NonNull Round round, @NonNull State state) {
        // no-op
    }

    @Override
    public void onSealConsensusRound(@NonNull Round round, @NonNull State state) {
        // Touch this round
        round.getRoundNum();
    }

    @Override
    public void onStateInitialized(
            @NonNull State state,
            @NonNull Platform platform,
            @NonNull InitTrigger trigger,
            @Nullable SoftwareVersion previousVersion) {
        // no-op
    }

    @Override
    public void onUpdateWeight(
            @NonNull MerkleStateRoot state, @NonNull AddressBook configAddressBook, @NonNull PlatformContext context) {
        // no-op
    }

    @Override
    public void onNewRecoveredState(@NonNull MerkleStateRoot recoveredState) {
        // no-op
    }
}
