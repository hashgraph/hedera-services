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

package com.hedera.node.app.fixtures.state;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.swirlds.state.State;
import com.swirlds.state.spi.EmptyReadableStates;
import com.swirlds.state.spi.EmptyWritableStates;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableSingletonStateBase;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableSingletonStateBase;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.test.fixtures.ListReadableQueueState;
import com.swirlds.state.test.fixtures.ListWritableQueueState;
import com.swirlds.state.test.fixtures.MapReadableKVState;
import com.swirlds.state.test.fixtures.MapReadableStates;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A useful test double for {@link State}. Works together with {@link MapReadableStates} and other fixtures.
 */
public class FakeState implements State {
    // Key is Service, value is Map of state name to HashMap or List or Object (depending on state type)
    private final Map<String, Map<String, Object>> states = new ConcurrentHashMap<>();
    private final Map<String, ReadableStates> readableStates = new ConcurrentHashMap<>();
    private final Map<String, WritableStates> writableStates = new ConcurrentHashMap<>();

    /**
     * Adds to the service with the given name the {@link ReadableKVState} {@code states}
     */
    public FakeState addService(@NonNull final String serviceName, @NonNull final Map<String, ?> dataSources) {
        final var serviceStates = this.states.computeIfAbsent(serviceName, k -> new ConcurrentHashMap<>());
        serviceStates.putAll(dataSources);
        // Purge any readable or writable states whose state definitions are now stale,
        // since they don't include the new data sources we just added
        readableStates.remove(serviceName);
        writableStates.remove(serviceName);
        return this;
    }

    /**
     * Removes the state with the given key for the service with the given name.
     *
     * @param serviceName the name of the service
     * @param stateKey the key of the state
     */
    public void removeServiceState(@NonNull final String serviceName, @NonNull final String stateKey) {
        requireNonNull(serviceName);
        requireNonNull(stateKey);
        this.states.computeIfPresent(serviceName, (k, v) -> {
            v.remove(stateKey);
            return v;
        });
    }

    @NonNull
    @Override
    @SuppressWarnings("java:S3740") // provide the parameterized type for the generic state variable
    public ReadableStates getReadableStates(@NonNull String serviceName) {
        return readableStates.computeIfAbsent(serviceName, s -> {
            final var serviceStates = this.states.get(s);
            if (serviceStates == null) {
                return new EmptyReadableStates();
            }
            final Map<String, Object> states = new ConcurrentHashMap<>();
            for (final var entry : serviceStates.entrySet()) {
                final var stateName = entry.getKey();
                final var state = entry.getValue();
                if (state instanceof Queue queue) {
                    states.put(stateName, new ListReadableQueueState(stateName, queue));
                } else if (state instanceof Map map) {
                    states.put(stateName, new MapReadableKVState(stateName, map));
                } else if (state instanceof AtomicReference ref) {
                    states.put(stateName, new ReadableSingletonStateBase<>(stateName, ref::get));
                }
            }
            return new MapReadableStates(states);
        });
    }

    @NonNull
    @Override
    @SuppressWarnings("java:S3740") // provide the parameterized type for the generic state variable
    public WritableStates getWritableStates(@NonNull String serviceName) {
        return writableStates.computeIfAbsent(serviceName, s -> {
            final var serviceStates = states.get(s);
            if (serviceStates == null) {
                return new EmptyWritableStates();
            }
            final Map<String, Object> data = new ConcurrentHashMap<>();
            for (final var entry : serviceStates.entrySet()) {
                final var stateName = entry.getKey();
                final var state = entry.getValue();
                if (state instanceof Queue queue) {
                    data.put(stateName, new ListWritableQueueState(stateName, queue));
                } else if (state instanceof Map map) {
                    data.put(stateName, new MapWritableKVState(stateName, map));
                } else if (state instanceof AtomicReference ref) {
                    data.put(stateName, new WritableSingletonStateBase(stateName, ref::get, ref::set));
                }
            }
            return new MapWritableStates(data, () -> readableStates.remove(serviceName));
        });
    }

    /**
     * Commits all pending changes made to the states.
     */
    public void commit() {
        writableStates.values().forEach(writableStates -> {
            if (writableStates instanceof MapWritableStates mapWritableStates) {
                mapWritableStates.commit();
            }
        });
    }
}
