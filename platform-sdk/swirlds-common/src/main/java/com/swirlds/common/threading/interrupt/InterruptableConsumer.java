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

package com.swirlds.common.threading.interrupt;

import java.util.function.Consumer;

/**
 * A variant of a {@link Consumer} that can be interrupted.
 */
@FunctionalInterface
public interface InterruptableConsumer<T> {

    /**
     * Handle an item from the queue.
     *
     * @param item
     * 		an item from the queue. Will never be null.
     * @throws InterruptedException
     * 		if the thread is interrupted while work is being done
     */
    void accept(T item) throws InterruptedException;
}
