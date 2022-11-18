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
 * Defines a registry of states for services.
 *
 * <p>Each service instance must, upon construction, define any states it wants to use. This is done
 * by calling the {@link #registerOrMigrate(String, StateRegistryCallback)} method, which can be
 * used for all different state management tasks: genesis, migration, removal, etc.
 */
public interface StateRegistry {
    /**
     * Called by the service during initialization to register a new state if none is present, or
     * migrate one if desired. or delete one from the underlying storage. The service will call this
     * method with a {@code stateKey} that represents a state that it knows about. The {@link
     * StateRegistryCallback} lambda is then invoked by the registry for the following two
     * conditions:
     *
     * <ol>
     *   <li>A {@link WritableState} for that {@code stateKey} was found. The callback will be
     *       provided with a {@link StateDefinition} that can be used to create a new {@link
     *       WritableState} to replace this one if desired. The {@link WritableState} found will
     *       also be supplied. The state returned by the lambda will be the one stored in the
     *       registry. If null, nothing will be registered and any state that was registered will be
     *       removed.
     *   <li>A {@link WritableState} was not found. The callback will be provided with a {@link
     *       StateDefinition} but with no {@link WritableState}. The callback can create and return
     *       the state to use, or it can return null if it wants no state registered.
     * </ol>
     *
     * <p>For example, suppose my service used to have an in-memory state but wants to migrate to an
     * on-disk state. It may be that the service is starting with an existing state (upgrade), or
     * with no existing state (genesis). In either case, my code may look like this:
     *
     * <pre>
     *     registry.registerOrMigrate(MyStateKey.SOME_STATE, (builder, existingState) -> {
     *        if (existingState.isEmpty()) {
     *            // This is the genesis case, use the builder to create the state definition
     *            // and return the built state
     *            return builder.onDisk(MyStateKey.SOME_STATE)
     *            		.keySerializer(...)
     *            	    .valueSerializer(...)
     *            	    .build();
     *        } else {
     *        	  // If I do not want to do a migration, I can just return existingState.get().
     *        	  // If I want to do a migration in place, I can do it by using existingState.get()
     *        	  // and making any modifications I want to. Or if I want to migrate, say, from
     *        	  // in-memory to on-disk, I could do it like this:
     *            final var newStateOnDisk = builder.onDisk(MyStateKey.SOME_STATE)
     *            		.keySerializer(...)
     *            	    .valueSerializer(...)
     *            	    .build();
     *
     *            final var existingStateInMemory = existingState.get();
     *
     *            // ... write code here to migrate from existingStateInMemory to newStateOnDisk
     *
     *            // Return the new state to make it permanent in the registry
     *            return newStateOnDisk;
     *        }
     *     });
     * </pre>
     */
    <K, V> void registerOrMigrate(String stateKey, StateRegistryCallback<K, V> createOrMigrate);
}
