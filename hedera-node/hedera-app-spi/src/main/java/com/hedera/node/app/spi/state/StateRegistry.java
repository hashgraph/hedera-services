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

import com.swirlds.common.system.SoftwareVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Defines a registry of states for a service module. When a new service instance is created, the
 * application will provide an instance of the {@link StateRegistry} to the service. The service
 * instance may inspect existing state, create new state, migrate state, or delete state.
 *
 * <p>State keys must have at least one character. All characters in a state key may only be:
 *
 * <ul>
 *   <li>{@link Character#isAlphabetic(int)}
 *   <li>{@link Character#isSpaceChar(int)}
 *   <li>{@link Character#isDigit(int)}
 * </ul>
 *
 * <pre>{@code
 * public MyConstructor(StateRegistry r) {
 *     final var oldFoo = r.readableState("OLD_FOO", OldFooKeyParser::new, OldFooParser::new);
 *
 *     // Every state you want, must be registered
 *     r.register("FOO")
 *          .memory() // use in-memory state
 *          .keyWriter(FooKey::write)
 *          .valueWriter(Foo::write)
 *          .keyParser(FooKeyParser::new)
 *          .valueParser(FooParser::new)
 *          .onMigrate((oldState, newState) -> {
 *              // ... do migration
 *          })
 *          .complete();
 * }
 * }</pre>
 */
public interface StateRegistry {

    /**
     * Gets the current {@link SoftwareVersion} of this application at the time of startup. This may
     * be different from the {@link #getPreviousVersion()} if the system is starting with an older,
     * existing body of state from an older version.
     *
     * @return The version of the current system.
     */
    @NonNull
    SoftwareVersion getCurrentVersion();

    /**
     * Gets the {@link SoftwareVersion} of the state at the time of startup. This may be different
     * from {@link #getCurrentVersion()} if the system is starting with an older, existing body of
     * state from an older version.
     *
     * @return The version of the system's state that was loaded, or {@link
     *     SoftwareVersion#NO_VERSION}.
     */
    @Nullable
    SoftwareVersion getPreviousVersion();

    /**
     * Register every state the service supports. If the state file being loaded by the system
     * includes old states that are to be ignored, or migrated, they must be part of the
     * registration process, otherwise the state file will fail to load and the application will
     * fail to start.
     *
     * @param stateKey The state key. Cannot be null and must be a valid state key.
     * @return A {@link StateRegistrationBuilder} for specifying the registration details.
     */
    @NonNull
    StateRegistrationBuilder register(@NonNull String stateKey);

    /**
     * Removes the specified state from the registry.
     *
     * @param stateKey The key of the state to remove
     * @param keyParser The key parser used with the old state
     * @param valueParser The value parser used with the old state
     * @param keyWriter The key writer used with the old state
     * @param valueWriter The value writer used with the old state
     * @param keyRuler The key ruler used with the old state
     */
    <K, V> void remove(
            @NonNull String stateKey,
            @NonNull Parser<K> keyParser,
            @NonNull Parser<V> valueParser,
            @NonNull Writer<K> keyWriter,
            @NonNull Writer<V> valueWriter,
            @Nullable Ruler keyRuler);
}
