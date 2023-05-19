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

package com.hedera.node.app.state.stack;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.state.WritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Iterator;
import java.util.Set;

public class WritableKVStateStack<K, V> implements WritableKVState<K, V> {

    private final WritableStatesStack writableStatesStack;
    private final String stateKey;

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

    @Nullable
    @Override
    public V getForModify(@NonNull K key) {
        return getCurrent().getForModify(key);
    }

    @Override
    public void put(@NonNull K key, @NonNull V value) {
        getCurrent().put(key, value);
    }

    @Override
    public void remove(@NonNull K key) {
        getCurrent().remove(key);
    }

    @Override
    @NonNull
    public Iterator<K> keys() {
        return getCurrent().keys();
    }

    @NonNull
    @Override
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
}
