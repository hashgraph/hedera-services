/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.state.StateChangesListener.DataType.MAP;
import static com.hedera.node.app.state.StateChangesListener.DataType.QUEUE;
import static com.hedera.node.app.state.StateChangesListener.DataType.SINGLETON;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.state.WrappedWritableKVState;
import com.hedera.node.app.spi.state.WrappedWritableQueueState;
import com.hedera.node.app.spi.state.WrappedWritableSingletonState;
import com.swirlds.state.spi.CommittableWritableStates;
import com.swirlds.state.spi.KVChangeListener;
import com.swirlds.state.spi.QueueChangeListener;
import com.swirlds.state.spi.SingletonChangeListener;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableQueueState;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A wrapper around a {@link WritableStates} that provides a wrapped instances of {@link WritableKVState} and
 * {@link WritableSingletonState} of a given {@link WritableStates} delegate.
 */
public class WrappedWritableStates implements WritableStates {

    private final WritableStates delegate;
    private final Map<String, WrappedWritableKVState<?, ?>> writableKVStateMap = new HashMap<>();
    private final Map<String, WrappedWritableSingletonState<?>> writableSingletonStateMap = new HashMap<>();
    private final Map<String, WrappedWritableQueueState<?>> writableQueueStateMap = new HashMap<>();

    /**
     * Constructs a {@link WrappedWritableStates} that wraps the given {@link WritableStates}.
     *
     * @param delegate the {@link WritableStates} to wrap
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    public WrappedWritableStates(@NonNull final WritableStates delegate) {
        this.delegate = requireNonNull(delegate, "delegate must not be null");
    }

    /**
     * Registers the given {@link StateChangesListener} with this {@link WrappedWritableStates} for the provided
     * service name and its requested target data types.
     * @param serviceName the name of the service
     * @param listener the {@link StateChangesListener} to register
     */
    public void register(@NonNull final String serviceName, @NonNull final StateChangesListener listener) {
        Objects.requireNonNull(listener);
        if (listener.targetDataTypes().contains(MAP)) {
            writableKVStateMap.values().forEach(state -> registerKvListener(serviceName, state, listener));
        }
        if (listener.targetDataTypes().contains(QUEUE)) {
            writableQueueStateMap.values().forEach(state -> registerQueueListener(serviceName, state, listener));
        }
        if (listener.targetDataTypes().contains(SINGLETON)) {
            writableSingletonStateMap
                    .values()
                    .forEach(state -> registerSingletonListener(serviceName, state, listener));
        }
    }

    @Override
    public boolean contains(@NonNull String stateKey) {
        return delegate.contains(stateKey);
    }

    @Override
    @NonNull
    public Set<String> stateKeys() {
        return delegate.stateKeys();
    }

    @SuppressWarnings("unchecked")
    @Override
    @NonNull
    public <K, V> WritableKVState<K, V> get(@NonNull String stateKey) {
        return (WritableKVState<K, V>)
                writableKVStateMap.computeIfAbsent(stateKey, s -> new WrappedWritableKVState<>(delegate.get(stateKey)));
    }

    @SuppressWarnings("unchecked")
    @Override
    @NonNull
    public <T> WritableSingletonState<T> getSingleton(@NonNull String stateKey) {
        return (WritableSingletonState<T>) writableSingletonStateMap.computeIfAbsent(
                stateKey, s -> new WrappedWritableSingletonState<>(delegate.getSingleton(stateKey)));
    }

    @SuppressWarnings("unchecked")
    @Override
    @NonNull
    public <E> WritableQueueState<E> getQueue(@NonNull String stateKey) {
        return (WritableQueueState<E>) writableQueueStateMap.computeIfAbsent(
                stateKey, s -> new WrappedWritableQueueState<>(delegate.getQueue(stateKey)));
    }

    /**
     * Returns {@code true} if the state of this {@link WrappedWritableStates} has been modified.
     *
     * @return {@code true}, if the state has been modified; otherwise {@code false}
     */
    public boolean isModified() {
        for (WrappedWritableKVState<?, ?> kvState : writableKVStateMap.values()) {
            if (kvState.isModified()) {
                return true;
            }
        }
        for (WrappedWritableQueueState<?> queueState : writableQueueStateMap.values()) {
            if (queueState.isModified()) {
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

    /**
     * Writes all modifications to the underlying {@link WritableStates}.
     */
    public void commit() {
        for (WrappedWritableKVState<?, ?> kvState : writableKVStateMap.values()) {
            kvState.commit();
        }
        for (WrappedWritableQueueState<?> queueState : writableQueueStateMap.values()) {
            queueState.commit();
        }
        for (WrappedWritableSingletonState<?> singletonState : writableSingletonStateMap.values()) {
            singletonState.commit();
        }

        if (delegate instanceof CommittableWritableStates terminalStates) {
            terminalStates.commit();
        }
    }

    private void registerSingletonListener(
            @NonNull final String serviceName,
            @NonNull final WrappedWritableSingletonState<?> singletonState,
            @NonNull final StateChangesListener listener) {
        final var stateName = serviceName + "." + singletonState.getStateKey();
        singletonState.registerSingletonListener(new SingletonChangeListener() {
            @Override
            public <V> void singletonUpdateChange(@NonNull final V value) {
                listener.singletonUpdateChange(stateName, value);
            }
        });
    }

    private void registerQueueListener(
            @NonNull final String serviceName,
            @NonNull final WrappedWritableQueueState<?> queueState,
            @NonNull final StateChangesListener listener) {
        final var stateName = serviceName + "." + queueState.getStateKey();
        queueState.registerQueueListener(new QueueChangeListener() {
            @Override
            public <V> void queuePushChange(@NonNull V value) {
                listener.queuePushChange(stateName, value);
            }

            @Override
            public void queuePopChange() {
                listener.queuePopChange(stateName);
            }
        });
    }

    private void registerKvListener(
            @NonNull final String serviceName,
            @NonNull final WrappedWritableKVState<?, ?> kvState,
            @NonNull final StateChangesListener listener) {
        final var stateName = serviceName + "." + kvState.getStateKey();
        kvState.registerKvListener(new KVChangeListener() {
            @Override
            public <K, V> void mapUpdateChange(@NonNull final K key, @NonNull V value) {
                listener.mapUpdateChange(stateName, key, value);
            }

            @Override
            public <K> void mapDeleteChange(@NonNull final K key) {
                listener.mapDeleteChange(stateName, key);
            }
        });
    }
}
