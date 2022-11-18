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

import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Callback invoked during the registration process in {@link StateRegistry}.
 *
 * <p>When a service instance desires to work with the state registry, it does so by invoking the
 * {@link StateRegistry#registerOrMigrate(String, StateRegistryCallback)} method, which takes this
 * callback as its second argument. There are four cases the callback needs to deal with:
 *
 * <ol>
 *   <li><b>Genesis:</b> It may be that the state for that {@code stateKey} does not yet exist, and
 *       needs to be created. The callback uses the supplied {@link StateDefinition} to define the
 *       state and return it from the callback.
 *   <li><b>Migration:</b> It may be that the state already exists, but needs to be migrated to a
 *       newer representation. The callback uses the supplied {@link Optional< WritableState >} to
 *       access the existing state. If migration is in-place, the callback can simply modify the
 *       state directly and return the existing state from the callback. If the migration is from
 *       one {@link WritableState} to another (such as when migrating from in-memory to on-disk
 *       states), then a new {@link WritableState} is created by the {@link StateDefinition} and the
 *       callback can use the old and new {@link WritableState}s to migrate from one to the other,
 *       and then return the new state from the callback.
 *   <li><b>Removal:</b> It may be that the service wants to remove a previous state it used to use.
 *       In this case, the callback simply returns {@code null} from the method.
 *   <li><b>No Change:</b> It may be that this is not a genesis situation, and there is no migration
 *       or removal needed. In this case, the callback simply returns the supplied state.
 * </ol>
 *
 * @param <K> The key used with the {@link WritableState}
 * @param <V> The value used with the {@link WritableState}
 */
public interface StateRegistryCallback<K, V>
        extends BiFunction<StateDefinition, Optional<WritableState<K, V>>, WritableState<K, V>> {}
