/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.fcqueue;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.fcqueue.internal.FCQueueNode;

final class AccessibleFCQueue<E extends FastCopyable & SerializableHashable> extends SlowMockFCQueue<E> {

    public AccessibleFCQueue() {
        super();
    }

    private AccessibleFCQueue(final SlowMockFCQueue<E> queue) {
        super(queue);
    }

    protected FCQueueNode<E> getHead() {
        return this.head;
    }

    protected FCQueueNode<E> getTail() {
        return this.tail;
    }

    public AccessibleFCQueue<E> copy() {
        return new AccessibleFCQueue<>(super.copy());
    }
}
