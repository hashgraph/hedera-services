/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.base.state;

import javax.annotation.Nonnull;

/**
 * A set of {@link State} used by a service. Any service may have one or more {@link State}s, and
 * this set is made available to the service during transaction handling by the application.
 */
public interface States {
    /**
     * Gets the {@link State} associated with the given stateKey. If the state cannot be found, an
     * exception is thrown. This should **never** happen in an application, and represents a fatal
     * bug.
     *
     * @param stateKey The key used for looking up state
     * @return The State for that key. This will never be null.
     * @param <K> The key type in the State.
     * @param <V> The value type in the State.
     * @throws NullPointerException if stateKey is null.
     * @throws IllegalArgumentException if the state cannot be found.
     */
    @Nonnull
    <K, V> State<K, V> get(@Nonnull String stateKey);
}
