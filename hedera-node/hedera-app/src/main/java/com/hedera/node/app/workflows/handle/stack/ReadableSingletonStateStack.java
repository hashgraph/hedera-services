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

import com.hedera.node.app.spi.state.ReadableSingletonState;
import com.hedera.node.app.spi.state.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * An implementation of {@link ReadableSingletonState} that delegates to the current {@link ReadableSingletonState} in a
 * {@link com.hedera.node.app.spi.workflows.HandleContext.SavepointStack}.
 *
 * <p>A {@link com.hedera.node.app.spi.workflows.HandleContext.SavepointStack} consists of a stack of frames, each of
 * which contains a set of modifications in regard to the state of the underlying frame. On the top of the stack is the
 * most recent state. This class delegates to the current {@link ReadableSingletonState} on top of such a stack.
 *
 * @param <T> the type of the singleton state
 */
public class ReadableSingletonStateStack<T> implements ReadableSingletonState<T> {

    private final ReadableStatesStack readableStatesStack;
    private final String stateKey;

    /**
     * Constructs a {@link ReadableSingletonStateStack} that delegates to the current {@link ReadableSingletonState} in
     * the given {@link ReadableStatesStack} for the given state key. A {@link ReadableStatesStack} is an implementation
     * of {@link ReadableStates} that delegates to the most recent version in a
     * {@link com.hedera.node.app.spi.workflows.HandleContext.SavepointStack}
     *
     * @param readableStatesStack the {@link ReadableStatesStack}
     * @param stateKey the state key
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public ReadableSingletonStateStack(
            @NonNull final ReadableStatesStack readableStatesStack, @NonNull final String stateKey) {
        this.readableStatesStack = requireNonNull(readableStatesStack, "readableStates must not be null");
        this.stateKey = requireNonNull(stateKey, "stateKey must not be null");
    }

    @NonNull
    private ReadableSingletonState<T> getCurrent() {
        return readableStatesStack.getCurrent().getSingleton(stateKey);
    }

    @NonNull
    @Override
    public String getStateKey() {
        return getCurrent().getStateKey();
    }

    @Nullable
    @Override
    public T get() {
        return getCurrent().get();
    }

    @Override
    public boolean isRead() {
        return getCurrent().isRead();
    }
}
