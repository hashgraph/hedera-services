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

import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * An implementation of {@link WritableSingletonState} that delegates to the current {@link WritableSingletonState} in a
 * {@link com.hedera.node.app.spi.workflows.HandleContext.SavepointStack}.
 *
 * <p>A {@link com.hedera.node.app.spi.workflows.HandleContext.SavepointStack} consists of a stack of frames, each of
 * which contains a set of modifications in regard to the state of the underlying frame. On the top of the stack is the
 * most recent state. This class delegates to the current {@link WritableSingletonState} on top of such a stack.
 *
 * <p>All changes made to the {@link WritableSingletonStateStack} are applied to the frame on top of the stack.
 * Consequently, all frames added later on top of the current frame will see the changes. If the frame is removed
 * however, the changes are lost.
 *
 * @param <T> the type of the singleton state
 */
public class WritableSingletonStateStack<T> implements WritableSingletonState<T> {

    private final WritableStatesStack writableStatesStack;
    private final String stateKey;

    /**
     * Constructs a {@link WritableSingletonStateStack} that delegates to the current {@link WritableSingletonState} in
     * the given {@link WritableStatesStack} for the given state key. A {@link WritableStatesStack} is an implementation
     * of {@link WritableStates} that delegates to the most recent version in a
     * {@link com.hedera.node.app.spi.workflows.HandleContext.SavepointStack}
     *
     * @param writableStatesStack the {@link WritableStatesStack}
     * @param stateKey the state key
     * @throws NullPointerException if any of the arguments is {@code null}
     */
    public WritableSingletonStateStack(
            @NonNull final WritableStatesStack writableStatesStack, @NonNull final String stateKey) {
        this.writableStatesStack = requireNonNull(writableStatesStack, "writableStatesStack must not be null");
        this.stateKey = requireNonNull(stateKey, "stateKey must not be null");
    }

    @NonNull
    private WritableSingletonState<T> getCurrent() {
        return writableStatesStack.getCurrent().getSingleton(stateKey);
    }

    @Override
    @NonNull
    public String getStateKey() {
        return stateKey;
    }

    @Override
    @Nullable
    public T get() {
        return getCurrent().get();
    }

    @Override
    public boolean isRead() {
        return getCurrent().isRead();
    }

    @Override
    public void put(@Nullable final T value) {
        getCurrent().put(value);
    }

    @Override
    public boolean isModified() {
        return getCurrent().isModified();
    }
}
