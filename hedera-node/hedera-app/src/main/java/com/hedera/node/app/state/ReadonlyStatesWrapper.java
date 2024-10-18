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

import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableQueueState;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * A wrapper around a {@link WritableStates} that creates read-only versions of the underlying
 * writable state, which is needed in some scenarios to make changes visible.
 */
public class ReadonlyStatesWrapper implements ReadableStates {

    private final WritableStates delegate;

    /**
     * Create a new wrapper around the given {@code delegate}.
     *
     * @param delegate the {@link WritableStates} to wrap
     */
    public ReadonlyStatesWrapper(@NonNull final WritableStates delegate) {
        this.delegate = delegate;
    }

    @NonNull
    @Override
    public <K, V extends Record> ReadableKVState<K, V> get(@NonNull String stateKey) {
        return new ReadonlyKVStateWrapper<>(delegate.get(stateKey));
    }

    @NonNull
    @Override
    public <T> ReadableSingletonState<T> getSingleton(@NonNull String stateKey) {
        return new ReadonlySingletonStateWrapper<>(delegate.getSingleton(stateKey));
    }

    @NonNull
    @Override
    public <E> ReadableQueueState<E> getQueue(@NonNull String stateKey) {
        return new ReadonlyQueueStateWrapper<>(delegate.getQueue(stateKey));
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
}
