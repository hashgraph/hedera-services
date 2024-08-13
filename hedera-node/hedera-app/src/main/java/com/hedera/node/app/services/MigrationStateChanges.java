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

package com.hedera.node.app.services;

import static com.hedera.hapi.block.stream.output.StateChangesCause.STATE_CHANGE_CAUSE_MIGRATION;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.node.app.blocks.impl.KVStateChangeListener;
import com.hedera.node.app.blocks.impl.RoundStateChangeListener;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Captures the state changes that occur during a migration.
 */
public class MigrationStateChanges {
    private final List<List<StateChange>> stateChanges = new ArrayList<>();
    private final KVStateChangeListener kvStateChangeListener = new KVStateChangeListener();
    private final RoundStateChangeListener roundStateChangeListener = new RoundStateChangeListener(Instant.EPOCH);

    /**
     * Constructs a new instance of {@link MigrationStateChanges} based on migration
     * changes to the given state.
     * @param state The state to track changes on
     */
    public MigrationStateChanges(@NonNull final State state) {
        state.registerCommitListener(kvStateChangeListener);
        state.registerCommitListener(roundStateChangeListener);
    }

    /**
     * If any key/value changes have been made since the last call, inserts a {@link BlockItem}
     * boundary into the state changes, necessary so that block nodes can commit the same
     * transactional units into {@link com.swirlds.state.spi.WritableKVState} instances.
     */
    public void trackCommit() {
        final var maybeKvChanges = kvStateChangeListener.getStateChanges();
        if (!maybeKvChanges.isEmpty()) {
            stateChanges.add(new ArrayList<>(maybeKvChanges));
            kvStateChangeListener.resetStateChanges();
        }
    }

    /**
     * Returns the state changes that occurred during the migration, in the form
     * of {@link StateChanges} builders that represent transactional units.
     * @return the state changes that occurred during the migration
     */
    public List<StateChanges.Builder> getStateChanges() {
        final var roundChanges = roundStateChangeListener.allStateChanges();
        if (!roundChanges.isEmpty()) {
            stateChanges.add(roundChanges);
        }
        return stateChanges.stream()
                .map(changes -> StateChanges.newBuilder()
                        .cause(STATE_CHANGE_CAUSE_MIGRATION)
                        .stateChanges(changes))
                .toList();
    }
}