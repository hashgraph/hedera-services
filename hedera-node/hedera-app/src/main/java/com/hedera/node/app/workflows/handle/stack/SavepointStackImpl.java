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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * The default implementation of {@link SavepointStack}.
 */
public class SavepointStackImpl implements SavepointStack, HederaState {

    private final HederaState root;
    private final Deque<WrappedHederaState> stack = new ArrayDeque<>();
    private final Map<String, WritableStatesStack> writableStatesMap = new HashMap<>();

    /**
     * Constructs a new {@link SavepointStackImpl} with the given root state.
     *
     * @param root the root state
     * @throws NullPointerException if {@code root} is {@code null}
     */
    public SavepointStackImpl(@NonNull final HederaState root) {
        this.root = requireNonNull(root, "root must not be null");
        setupSavepoint(root);
    }

    private void setupSavepoint(@NonNull final HederaState state) {
        final var newState = new WrappedHederaState(state);
        stack.push(newState);
    }

    @Override
    public void createSavepoint() {
        setupSavepoint(stack.isEmpty() ? root : peek());
    }

    @Override
    public void commit() {
        if (stack.size() <= 1) {
            throw new IllegalStateException("The savepoint stack is empty");
        }
        stack.pop().commit();
    }

    @Override
    public void rollback() {
        if (stack.size() <= 1) {
            throw new IllegalStateException("The savepoint stack is empty");
        }
        stack.pop();
    }

    /**
     * Commits all state changes captured in this stack.
     */
    public void commitFullStack() {
        while (!stack.isEmpty()) {
            stack.pop().commit();
        }
    }

    /**
     * Rolls back all state changes captured in this stack.
     */
    public void rollbackFullStack() {
        stack.clear();
        setupSavepoint(root);
    }

    @Override
    public int depth() {
        return stack.size();
    }

    /**
     * Returns the current {@link HederaState} without removing it from the stack.
     *
     * @return the current {@link HederaState}
     * @throws IllegalStateException if the stack has been committed already
     */
    @NonNull
    public WrappedHederaState peek() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("The stack has already been committed");
        }
        return stack.peek();
    }

    /**
     * Returns the root {@link ReadableStates} for the given service name.
     *
     * @param serviceName the name of the service
     * @return the root {@link ReadableStates} for the given service name
     */
    @NonNull
    public ReadableStates rootStates(@NonNull final String serviceName) {
        return root.createReadableStates(serviceName);
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
