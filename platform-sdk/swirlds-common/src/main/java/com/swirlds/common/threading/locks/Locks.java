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

package com.swirlds.common.threading.locks;

import com.swirlds.common.threading.locks.internal.AutoLock;
import com.swirlds.common.threading.locks.internal.DefaultIndexLock;
import com.swirlds.common.threading.locks.internal.ResourceLock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Factory for all custom Locks. Should be used as a facade for the API.
 */
public interface Locks {

    /**
     * Create a new lock for index values.
     *
     * @param parallelism
     * 		the number of unique locks. Higher parallelism reduces chances of collision for non-identical
     * 		* 		indexes at the cost of additional memory overhead.
     * @return a new lock for index values.
     */
    static IndexLock createIndexLock(final int parallelism) {
        return new DefaultIndexLock(parallelism);
    }

    /**
     * Creates a standard lock that provides the {@link AutoCloseable} semantics. Lock is reentrant.
     *
     * @return the lock
     */
    static AutoClosableLock createAutoLock() {
        return new AutoLock();
    }

    /**
     * Provides an implementation of {@link AutoClosableLock} which holds a resource that needs to be locked before it
     * can be used
     *
     * @param resource
     * 		the resource
     * @param <T>
     * 		type of the resource
     * @return the lock
     */
    static <T> AutoClosableResourceLock<T> createResourceLock(final T resource) {
        return new ResourceLock<>(new ReentrantLock(), resource);
    }
}
