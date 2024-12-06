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

package com.swirlds.state.merkle.queue;

import static java.util.Objects.requireNonNull;

import com.swirlds.state.spi.ReadableQueueState;
import com.swirlds.state.spi.ReadableQueueStateBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Iterator;

/**
 * An implementation of {@link ReadableQueueState} that uses a merkle {@link QueueNode} as the backing store.
 * @param <E> The type of elements in the queue.
 */
public class ReadableQueueStateImpl<E> extends ReadableQueueStateBase<E> {
    private final QueueNode<E> dataSource;

    /** Create a new instance */
    public ReadableQueueStateImpl(@NonNull final String stateKey, @NonNull final QueueNode<E> node) {
        super(stateKey);
        this.dataSource = requireNonNull(node);
    }

    @Nullable
    @Override
    protected E peekOnDataSource() {
        return dataSource.peek();
    }

    @NonNull
    @Override
    protected Iterator<E> iterateOnDataSource() {
        return dataSource.iterator();
    }
}
