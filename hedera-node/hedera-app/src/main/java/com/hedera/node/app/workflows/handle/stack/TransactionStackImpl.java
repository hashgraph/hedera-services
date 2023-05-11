/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.workflows.handle.stack;

import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionStack;
import com.hedera.node.app.workflows.handle.state.ReadableStatesStack;
import com.hedera.node.app.workflows.handle.state.WritableStatesStack;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class TransactionStackImpl implements TransactionStack {

    private final Deque<TransactionStackEntry> stack = new ArrayDeque<>();
    private final Map<String, WritableStatesStack> writableStatesMap = new HashMap<>();

    public TransactionStackImpl(@NonNull final TransactionStackEntry root) {
        stack.add(root);
    }

    @Override
    public void setSavepoint() {
        // TODO: Implement TransactionStackImpl.setSavepoint()
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
    public TransactionStackEntry peek() {
        assert !stack.isEmpty();
        return stack.peek();
    }

    @NonNull
    public ReadableStates createReadableStates(@NonNull String serviceName) {
        return new ReadableStatesStack(this, serviceName);
    }

    @NonNull
    public WritableStates getOrCreateWritableStates(@NonNull String serviceName) {
        return new WritableStatesStack(this, serviceName);
    }

    public void commit() {
        while (stack.size() > 1) {
            stack.pop().state().commit();
        }
    }
}
