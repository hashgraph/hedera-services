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

import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.spi.workflows.HandleContext.SavepointStack;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.ReadonlyStatesWrapper;
import com.hedera.node.app.state.WrappedHederaState;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * The default implementation of {@link SavepointStack}.
 */
public class SavepointStackImpl implements SavepointStack, HederaState {

    private final Deque<Savepoint> stack = new ArrayDeque<>();
    private final Map<String, WritableStatesStack> writableStatesMap = new HashMap<>();

    /**
     * Constructs a new {@link SavepointStackImpl} with the given root state.
     *
     * @param root the root state
     * @throws NullPointerException if {@code root} is {@code null}
     */
    public SavepointStackImpl(@NonNull final HederaState root, @NonNull final Configuration config) {
        requireNonNull(root, "root must not be null");
        requireNonNull(config, "config must not be null");
        setupSavepoint(root, config);
    }

    private void setupSavepoint(@NonNull final HederaState state, @NonNull final Configuration config) {
        final var newState = new WrappedHederaState(state);
        final var savepoint = new Savepoint(newState, config);
        stack.push(savepoint);
    }

    @Override
    public void createSavepoint() {
        setupSavepoint(peek().state(), peek().configuration());
    }

    @Override
    public void rollback(final int level) {
        if (stack.size() <= level) {
            throw new IllegalStateException("The transaction stack does not contain enough elements");
        }
        for (int i = 0; i < level; i++) {
            stack.pop();
        }
    }

    @Override
    public int depth() {
        return stack.size();
    }

    /**
     * Returns the current {@link Savepoint} without removing it from the stack.
     *
     * @return the current {@link Savepoint}
     * @throws IllegalStateException if the stack has been committed already
     */
    @NonNull
    public Savepoint peek() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("The stack has already been committed");
        }
        return stack.peek();
    }

    /**
     * Commits all state changes to the state that was provided when this {@link SavepointStackImpl} was created.
     */
    public void commit() {
        while (!stack.isEmpty()) {
            stack.pop().state().commit();
        }
    }

    /**
     * Sets the configuration of the current savepoint.
     *
     * @param configuration the configuration of the savepoint
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    public void configuration(@NonNull final Configuration configuration) {
        peek().configuration(configuration);
    }

    @Override
    @NonNull
    public ReadableStates createReadableStates(@NonNull final String serviceName) {
        return new ReadonlyStatesWrapper(createWritableStates(serviceName));
    }

    @Override
    @NonNull
    public WritableStates createWritableStates(@NonNull final String serviceName) {
        if (stack.isEmpty()) {
            throw new IllegalStateException("The stack has already been committed");
        }
        return writableStatesMap.computeIfAbsent(serviceName, s -> new WritableStatesStack(this, s));
    }
}
