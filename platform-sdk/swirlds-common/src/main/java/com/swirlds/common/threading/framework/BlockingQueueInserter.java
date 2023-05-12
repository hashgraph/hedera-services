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

package com.swirlds.common.threading.framework;

import java.util.concurrent.TimeUnit;

/**
 * Contains methods for inserting into a queue. Intentionally does not contain methods for reading values from a queue.
 *
 * @param <T>
 * 		the type of the object to be inserted
 */
public interface BlockingQueueInserter<T> {

    /**
     * Add an object to the queue, and throw if it is not possible to do so immediately. For additional
     * documentation see {@link java.util.concurrent.BlockingQueue#add(Object)}.
     *
     * @param t
     * 		the object to add
     * @return true
     * @throws IllegalStateException
     * 		if the queue is at capacity and the object can not be immediately added
     */
    boolean add(T t);

    /**
     * Offer an object to the queue. Object is added if there is available capacity.
     * For additional documentation see
     * {@link java.util.concurrent.BlockingQueue#offer(Object)}.
     *
     * @param t
     * 		the object being offered
     * @return true if the object was added, false if the object was not added
     */
    boolean offer(T t);

    /**
     * Offer an object to the queue. Object is added if there is available capacity before the timeout expires.
     * For additional documentation see
     * {@link java.util.concurrent.BlockingQueue#offer(Object, long, TimeUnit)}.
     *
     * @param t
     * 		the object being offered
     * @param timeout
     * 		the maximum time to wait
     * @param unit
     * 		the time unit of the timeout argument
     * @return true if the object was added, false if the object was not added
     */
    boolean offer(T t, long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Put an object the queue, waiting if necessary for space to become available. For additional documentation
     * see {@link java.util.concurrent.BlockingQueue#put(Object)}.
     *
     * @param t
     * 		the object to insert
     */
    void put(T t) throws InterruptedException;
}
