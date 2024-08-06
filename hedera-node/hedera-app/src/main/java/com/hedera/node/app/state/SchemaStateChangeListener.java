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

package com.hedera.node.app.state;

import com.hedera.hapi.block.stream.output.NewStateType;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An interface responsible for observing any state changes occurred on state
 * and some additional helper methods
 */
public interface SchemaStateChangeListener {
    /**
     * Addition of a new state.
     * This may be a singleton, virtual map, or queue state.
     *
     * @param stateName The name of the new state
     * @param type The type of the new state
     */
    default <K, V> void schemaAddStateChange(@NonNull final String stateName, @NonNull final NewStateType type) {}

    /**
     *  Removal of an existing state.
     *  The entire singleton, virtual map, or queue state is removed,
     *  and not just the contents.
     *
     * @param stateName The name of the state to be removed
     */
    default <V> void schemaRemoveStateChange(@NonNull final String stateName) {}
}
