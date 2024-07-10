/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.connector.impl;

/**
 * Similar to {@link java.util.concurrent.BlockingQueue} but with backpressure support.
 *
 * @param <E>
 */
public interface QueueWithBackpressure<E> {

    /**
     * Will block until the element is added to the queue.
     *
     * @param element the element to add
     * @throws InterruptedException
     */
    void put(E element) throws InterruptedException;

    /**
     * Will add the element to the queue if backpressure is not in effect.
     *
     * @param element the element to add
     * @return true if the element was added, false otherwise
     */
    boolean offer(E element);

    /**
     * Will add the element to the queue even if backpressure is in effect.
     *
     * @param element the element to add
     */
    void bypassBackpressure(E element);

    /**
     * Will block until an element is available in the queue.
     *
     * @return the element
     * @throws InterruptedException
     */
    E take() throws InterruptedException;
}
