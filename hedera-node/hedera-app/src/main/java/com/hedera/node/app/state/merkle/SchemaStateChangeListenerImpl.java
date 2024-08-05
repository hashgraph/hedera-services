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

package com.hedera.node.app.state.merkle;

import com.hedera.hapi.block.stream.output.NewStateChange;
import com.hedera.hapi.block.stream.output.NewStateType;
import com.hedera.hapi.block.stream.output.RemovedStateChange;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.node.app.state.SchemaStateChangeListener;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SchemaStateChangeListenerImpl implements SchemaStateChangeListener {
    private List<StateChange> stateChanges = new ArrayList<>();

    @Override
    public <K, V> void schemaAddStateChange(@NonNull final String stateName, @NonNull final NewStateType type) {
        Objects.requireNonNull(stateName, "stateName must not be null");
        Objects.requireNonNull(type, "type must not be null");

        final var change = NewStateChange.newBuilder().stateType(type).build();
        final var stateChange =
                StateChange.newBuilder().stateName(stateName).stateAdd(change).build();
        stateChanges.add(stateChange);
    }

    @Override
    public void schemaRemoveStateChange(@NonNull final String stateName) {
        Objects.requireNonNull(stateName, "stateName must not be null");

        final var stateChange = StateChange.newBuilder()
                .stateName(stateName)
                .stateRemove(new RemovedStateChange())
                .build();
        stateChanges.add(stateChange);
    }

    public List<StateChange> getStateChanges() {
        return stateChanges;
    }
}
