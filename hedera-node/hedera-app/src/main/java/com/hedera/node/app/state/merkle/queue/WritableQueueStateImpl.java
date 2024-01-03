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

package com.hedera.node.app.state.merkle.queue;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.state.WritableQueueStateBase;
import com.hedera.node.app.state.merkle.StateMetadata;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;

/**
 * An implementation of {@link com.hedera.node.app.spi.state.WritableQueueState} based on {@link QueueNode}.
 * @param <E> The type of element in the queue
 */
public class WritableQueueStateImpl<E> extends WritableQueueStateBase<E> {
    private final QueueNode<E> dataSource;

    public WritableQueueStateImpl(@NonNull final StateMetadata<?, E> md, @NonNull final QueueNode<E> node) {
        super(md.stateDefinition().stateKey());
        this.dataSource = requireNonNull(node);
    }

    @NonNull
    @Override
    protected Iterator<E> iterateOnDataSource() {
        return dataSource.iterator();
    }

    @Override
    protected void addToDataSource(@NonNull E element) {
        dataSource.add(element);
    }

    @Override
    protected void removeFromDataSource() {
        dataSource.remove();
    }
}
