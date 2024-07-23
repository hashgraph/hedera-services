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

import com.hedera.node.app.spi.workflows.HandleContext.SavepointStack;
import com.hedera.node.app.state.ReadonlyStatesWrapper;
import com.hedera.node.app.state.WrappedMerkleState;
import com.swirlds.state.MerkleState;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;

/**
 * The default implementation of {@link SavepointStack}.
 */
public class SavepointStackImpl implements SavepointStack, MerkleState {

    private final MerkleState root;
    private final Deque<WrappedMerkleState> stack = new ArrayDeque<>();
    private final Map<String, WritableStatesStack> writableStatesMap = new HashMap<>();

    /**
     * Constructs a new {@link SavepointStackImpl} with the given root state.
     *
     * @param root the root state
     * @throws NullPointerException if {@code root} is {@code null}
     */
    @Inject
    public SavepointStackImpl(@NonNull final MerkleState root) {
        this.root = requireNonNull(root, "root must not be null");
        setupSavepoint(root);
    }

    private void setupSavepoint(@NonNull final MerkleState state) {
        final var newState = new WrappedMerkleState(state);
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
        setupSavepoint(root);
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
     * Returns the current {@link MerkleState} without removing it from the stack.
     *
     * @return the current {@link MerkleState}
     * @throws IllegalStateException if the stack has been committed already
     */
    @NonNull
    public WrappedMerkleState peek() {
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
        return root.getReadableStates(serviceName);
    }

    /**
     * {@inheritDoc}
     *
     * The {@link ReadableStates} instances returned from this method are based on the {@link WritableStates} instances
     * for the same service name. This means that any modifications to the {@link WritableStates} will be reflected
     * in the {@link ReadableStates} instances returned from this method.
     * <p>
     * Unlike other {@link MerkleState} implementations, the returned {@link ReadableStates} of this implementation
     * must only be used in the handle workflow.
     */
    @Override
    @NonNull
    public ReadableStates getReadableStates(@NonNull String serviceName) {
        return new ReadonlyStatesWrapper(getWritableStates(serviceName));
    }

    /**
     * {@inheritDoc}
     *
     * This method guarantees that the same {@link WritableStates} instance is returned for the same {@code serviceName}
     * to ensure all modifications to a {@link WritableStates} are kept together.
     */
    @Override
    @NonNull
    public WritableStates getWritableStates(@NonNull final String serviceName) {
        if (stack.isEmpty()) {
            throw new IllegalStateException("The stack has already been committed");
        }
        return writableStatesMap.computeIfAbsent(serviceName, s -> new WritableStatesStack(this, s));
    }
}
