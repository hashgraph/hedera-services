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
import com.hedera.node.app.state.RecordCache;
import com.hedera.node.app.state.WrappedHederaState;
import com.hedera.node.config.ConfigProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class SavepointStackImpl implements SavepointStack, HederaState {

    private final ConfigProvider configProvider;
    private final Deque<Savepoint> stack = new ArrayDeque<>();
    private final Map<String, WritableStatesStack> writableStatesMap = new HashMap<>();

    public SavepointStackImpl(@NonNull final ConfigProvider configProvider, @NonNull final HederaState root) {
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
        setupSavepoint(root);
    }

    private void setupSavepoint(@NonNull HederaState state) {
        final var newState = new WrappedHederaState(state);
        final var newConfig = configProvider.getConfiguration();
        final var savepoint = new Savepoint(newState, newConfig);
        stack.push(savepoint);
    }

    @Override
    public void createSavepoint() {
        setupSavepoint(peek().state());
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

    @NonNull
    public Savepoint peek() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("The stack has already been committed");
        }
        return stack.peek();
    }

    public void commit() {
        while (!stack.isEmpty()) {
            stack.pop().state().commit();
        }
    }

    @Override
    @NonNull
    public ReadableStates createReadableStates(@NonNull final String serviceName) {
        return new ReadableStatesStack(this, serviceName);
    }

    @Override
    @NonNull
    public WritableStates createWritableStates(@NonNull String serviceName) {
        return writableStatesMap.computeIfAbsent(serviceName, s -> new WritableStatesStack(this, s));
    }

    @Override
    @NonNull
    public RecordCache getRecordCache() {
        throw new UnsupportedOperationException();
    }
}
