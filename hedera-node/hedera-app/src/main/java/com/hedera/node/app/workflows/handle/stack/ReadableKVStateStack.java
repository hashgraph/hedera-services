/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.stack;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Iterator;
import java.util.Set;

/**
 * An implementation of {@link ReadableKVState} that delegates to the current {@link ReadableKVState} in a
 * {@link com.hedera.node.app.spi.workflows.HandleContext.SavepointStack}.
 *
 * <p>A {@link com.hedera.node.app.spi.workflows.HandleContext.SavepointStack} consists of a stack of frames, each of
 * which contains a set of modifications in regard to the state of the underlying frame. On the top of the stack is the
 * most recent state. This class delegates to the current {@link ReadableKVState} on top of such a stack.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public class ReadableKVStateStack<K, V> implements ReadableKVState<K, V> {

    private final ReadableStatesStack readableStatesStack;
    private final String stateKey;

    /**
     * Constructs a {@link ReadableKVStateStack} that delegates to the current {@link ReadableKVState} in
     * the given {@link ReadableStatesStack} for the given state key. A {@link ReadableStatesStack} is an implementation
     * of {@link ReadableStates} that delegates to the most recent version in a
     * {@link com.hedera.node.app.spi.workflows.HandleContext.SavepointStack}
     *
     * @param readableStatesStack the {@link ReadableStatesStack}
     * @param stateKey the state key
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public ReadableKVStateStack(
            @NonNull final ReadableStatesStack readableStatesStack,
            @NonNull final String stateKey) {
        this.readableStatesStack = requireNonNull(readableStatesStack, "readableStates must not be null");
        this.stateKey = requireNonNull(stateKey, "stateKey must not be null");
    }

    @NonNull
    private ReadableKVState<K, V> getCurrent() {
        return readableStatesStack.getCurrent().get(stateKey);
    }

    @Override
    @NonNull
    public String getStateKey() {
        return stateKey;
    }

    @Override
    @Nullable
    public V get(@NonNull final K key) {
        return getCurrent().get(key);
    }

    @Override
    @NonNull
    public Iterator<K> keys() {
        return getCurrent().keys();
    }

    @Override
    @NonNull
    public Set<K> readKeys() {
        return getCurrent().readKeys();
    }

    @Override
    public long size() {
        return getCurrent().size();
    }
}
