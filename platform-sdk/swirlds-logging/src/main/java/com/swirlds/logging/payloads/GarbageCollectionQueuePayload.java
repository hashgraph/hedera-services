/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.logging.payloads;

/**
 * This payload is logged when the queue of an FCHashMapGarbageCollector grows too large.
 */
public class GarbageCollectionQueuePayload extends AbstractLogPayload {

    private int queueSize;

    public GarbageCollectionQueuePayload(final int queueSize) {
        super("FCHashMap garbage collection queue size exceeds threshold");
        this.queueSize = queueSize;
    }

    /**
     * Get the size of the queue.
     */
    public int getQueueSize() {
        return queueSize;
    }

    /**
     * Set the size of the queue.
     */
    public void setQueueSize(final int queueSize) {
        this.queueSize = queueSize;
    }
}
