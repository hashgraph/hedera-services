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
package com.hedera.node.app.service.mono.state.impl;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.spi.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A base class for implementations of {@link State}.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
public abstract class StateBase<K, V> implements State<K, V> {
    private final String stateKey;
    private final Map<K, V> readKeys = new HashMap<>();

    StateBase(@NonNull final String stateKey) {
        this.stateKey = Objects.requireNonNull(stateKey);
    }

    @Override
    public String getStateKey() {
        return stateKey;
    }

    /**
     * Reads the keys from the state
     *
     * @param key key to read from state
     * @return
     */
    protected abstract V read(K key);

    @Override
    public Optional<V> get(@NonNull final K key) {
        Objects.requireNonNull(key);
        return Optional.ofNullable(readKeys.computeIfAbsent(key, ignore -> read(key)));
    }

    @VisibleForTesting
    public Map<K, V> getReadKeys() {
        return readKeys;
    }
}
