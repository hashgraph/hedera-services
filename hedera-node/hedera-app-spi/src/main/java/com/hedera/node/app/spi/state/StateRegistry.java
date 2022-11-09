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

/**
 * Defines a registry of state for services.
 *
 * <p>Individual service modules need some backing key-value state for storing their own persistent
 * state. The merkle tree state is owned by the platform modules, with an implementation of {@code
 * SwirldState} being constructed by the application module. The application module implements this
 * interface, and gives an instance to service modules during their construction. Each service
 * module can look up its own state in the registry and, if that state is not present in the
 * register, create and register it.
 *
 * <p>It is also possible for the service module to replace the state it had previously registered,
 * or delete it. This is useful during migration at startup.
 */
public interface StateRegistry {
    /**
     * Called by the service during initialization to get a {@link State} from the registry, or
     * register a new one if none is present, or migrate one if desired. The service will call this
     * method with a {@code stateKey} that represents a state that it knows about. The {@code
     * createOrMigrate} lambda is then invoked by the registry for the following two conditions:
     *
     * <ol>
     *   <li>A {@link State} for that {@code stateKey} was found. The callback will be provided with
     *       a {@link StateBuilder} that can be used to create a new {@link State} to replace this
     *       one if desired. The {@link State} found will also be supplied. The state returned by
     *       the lambda will be the one stored in the registry. If null, nothing will be registered
     *       and any state that was registered will be removed.
     *   <li>A {@link State} was not found. The callback will be provided with a {@link
     *       StateBuilder} but with no {@link State}. The callback can create and return the state
     *       to use, or it can return null if it wants no state registered.
     * </ol>
     *
     * <p>For example, suppose my service used to have an in-memory state but wants to migrate to an
     * on-disk state. It may be that the service is starting with an existing state (upgrade), or
     * with no existing state (genesis). In either case, my code may look like this:
     *
     * <pre>
     *     registry.getOrRegister(MyStateKey.SOME_STATE, (builder, existingState) -> {
     *     // DOES NOT WORK. I need to be able to also distinguish the case where it is already
     *     // an on-disk state. That implies different keys (yuck) or versions (also yuck).
     *     // What else can I do?
     *        if (existingState.isPresent()) {
     *            final var newStateOnDisk = builder.onDisk(MyStateKey.SOME_STATE)
     *            		.keySerializer(...)
     *            	    .valueSerializer(...)
     *            	    .build();
     *
     *            // ... write code here to migrate from existingState to newStateOnDisk
     *            return newStateOnDisk;
     *        }
     *     });
     * </pre>
     */
    <K, V> void getOrRegister(String stateKey, StateRegistryCallback<K, V> createOrMigrate);
}
