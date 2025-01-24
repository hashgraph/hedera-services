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

package com.hedera.node.app.workflows.handle.stack;

import static java.util.Objects.requireNonNull;

import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.metrics.StoreMetrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Iterator;
import java.util.Set;

/**
 * An implementation of {@link WritableKVState} that delegates to the current {@link WritableKVState} in a
 * {@link com.hedera.node.app.spi.workflows.HandleContext.SavepointStack}.
 *
 * <p>A {@link com.hedera.node.app.spi.workflows.HandleContext.SavepointStack} consists of a stack of frames, each of
 * which contains a set of modifications in regard to the state of the underlying frame. On the top of the stack is the
 * most recent state. This class delegates to the current {@link WritableKVState} on top of such a stack.
 *
 * <p>All changes made to the {@link WritableKVStateStack} are applied to the frame on top of the stack. Consequently,
 * all frames added later on top of the current frame will see the changes. If the frame is removed however, the
 * changes are lost.
 *
 * @param <K> the type of the keys
 * @param <V> the type of the values
 */
public class WritableKVStateStack<K, V> implements WritableKVState<K, V> {

    private final WritableStatesStack writableStatesStack;
    private final String stateKey;

    /**
     * Constructs a {@link WritableKVStateStack} that delegates to the current {@link WritableKVState} in
     * the given {@link WritableStatesStack} for the given state key. A {@link WritableStatesStack} is an implementation
     * of {@link com.swirlds.state.spi.WritableStates} that delegates to the most recent version in a
     * {@link com.hedera.node.app.spi.workflows.HandleContext.SavepointStack}
     *
     * @param writableStatesStack the {@link WritableStatesStack}
     * @param stateKey the state key
     * @throws NullPointerException if any of the arguments is {@code null}
     */
    public WritableKVStateStack(
            @NonNull final WritableStatesStack writableStatesStack, @NonNull final String stateKey) {
        this.writableStatesStack = requireNonNull(writableStatesStack, "writableStatesStack must not be null");
        this.stateKey = requireNonNull(stateKey, "stateKey must not be null");
    }

    @NonNull
    private WritableKVState<K, V> getCurrent() {
        return writableStatesStack.getCurrent().get(stateKey);
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
    @Nullable
    public V getForModify(@NonNull final K key) {
        return getCurrent().getForModify(key);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public V getOriginalValue(@NonNull K key) {
        return (V) writableStatesStack.getRoot().get(stateKey).get(key);
    }

    @Override
    public void put(@NonNull final K key, @NonNull final V value) {
        getCurrent().put(key, value);
    }

    @Override
    public void remove(@NonNull final K key) {
        getCurrent().remove(key);
    }

    @Override
    @NonNull
    public Iterator<K> keys() {
        return getCurrent().keys();
    }

    @Override
    @NonNull
    public Set<K> modifiedKeys() {
        return getCurrent().modifiedKeys();
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

    @Override
    public void setMetrics(@NonNull StoreMetrics storeMetrics) {
        getCurrent().setMetrics(storeMetrics);
    }
}
