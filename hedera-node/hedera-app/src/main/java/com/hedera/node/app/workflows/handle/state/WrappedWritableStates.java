/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.workflows.handle.state;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.state.WrappedWritableKVState;
import com.hedera.node.app.spi.state.WrappedWritableSingletonState;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableSingletonState;
import com.hedera.node.app.spi.state.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class WrappedWritableStates implements WritableStates {

    private final WritableStates delegate;
    private final Map<String, WrappedWritableKVState<?, ?>> writableKVStateMap = new HashMap<>();
    private final Map<String, WrappedWritableSingletonState<?>> writableSingletonStateMap = new HashMap<>();

    public WrappedWritableStates(@NonNull final WritableStates delegate) {
        this.delegate = requireNonNull(delegate, "delegate must not be null");
    }
    @Override
    public boolean contains(@NonNull String stateKey) {
        return delegate.contains(stateKey);
    }

    @NonNull
    @Override
    public Set<String> stateKeys() {
        return delegate.stateKeys();
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <K, V> WritableKVState<K, V> get(@NonNull String stateKey) {
        return (WritableKVState<K, V>) writableKVStateMap
                .computeIfAbsent(stateKey, s -> new WrappedWritableKVState<>(delegate.get(stateKey)));
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <T> WritableSingletonState<T> getSingleton(@NonNull String stateKey) {
        return (WritableSingletonState<T>) writableSingletonStateMap
                .computeIfAbsent(stateKey, s -> new WrappedWritableSingletonState<>(delegate.getSingleton(stateKey)));
    }

    public boolean isModified() {
        for (WrappedWritableKVState<?, ?> kvState : writableKVStateMap.values()) {
            if (kvState.isModified()) {
                return true;
            }
        }
        for (WrappedWritableSingletonState<?> singletonState : writableSingletonStateMap.values()) {
            if (singletonState.isModified()) {
                return true;
            }
        }
        return false;
    }

    public void commit() {
        for (WrappedWritableKVState<?, ?> kvState : writableKVStateMap.values()) {
            kvState.commit();
        }
        for (WrappedWritableSingletonState<?> singletonState : writableSingletonStateMap.values()) {
            singletonState.commit();
        }
    }
}
