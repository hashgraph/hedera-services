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

package com.hedera.node.app.workflows.handle.state;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableSingletonState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class ReadableStatesStack implements ReadableStates {

    private final SavepointStackImpl stack;
    private final String statesName;
    private final Map<WrappedHederaState, ReadableStates> stackedReadableStates = new WeakHashMap<>();

    public ReadableStatesStack(SavepointStackImpl stack, String statesName) {
        this.stack = requireNonNull(stack, "stack must not be null");
        this.statesName = requireNonNull(statesName, "statesName must not be null");
    }

    @NonNull
    ReadableStates getCurrent() {
        return stackedReadableStates.computeIfAbsent(
                stack.peek().state(), hederaState -> hederaState.createReadableStates(statesName));
    }

    @NonNull
    @Override
    public <K, V> ReadableKVState<K, V> get(@NonNull String stateKey) {
        return new ReadableKVStateStack<>(this, stateKey);
    }

    @NonNull
    @Override
    public <T> ReadableSingletonState<T> getSingleton(@NonNull String stateKey) {
        return new ReadableSingletonStateStack<>(this, stateKey);
    }

    @Override
    public boolean contains(@NonNull String stateKey) {
        return getCurrent().contains(stateKey);
    }

    @Override
    @NonNull
    public Set<String> stateKeys() {
        return getCurrent().stateKeys();
    }
}
