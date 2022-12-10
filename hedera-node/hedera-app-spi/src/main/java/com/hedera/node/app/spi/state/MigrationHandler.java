/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.state;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Instances of this interface are passed to {@link StateRegistrationBuilder} to handle migration of
 * state.
 *
 * <p>When the system is first started, each {@link com.hedera.node.app.spi.Service} has a chance to
 * register states with the {@link StateRegistry} by using the {@link StateRegistrationBuilder}.
 * During that process, it may supply a {@link MigrationHandler} to be used to migrate whatever
 * state previously existed to some new state.
 *
 * @param <K> The type of the key used in the new state
 * @param <V> The type of the value used in the new state
 */
public interface MigrationHandler<K, V> {
    /**
     * Migrates the states in {@param oldStates} into {@param newStates}.
     *
     * <p>None of these states should be captured and stored. They are valid only for the scope of
     * this method call.
     *
     * @param oldStates An {@link ReadableStates} from which old states, defined in {@link
     *     StateRegistrationBuilder}, may be retrieved.
     * @param newState The new state object to write state into.
     */
    void onMigrate(@NonNull ReadableStates oldStates, @NonNull WritableState<K, V> newState);
}
