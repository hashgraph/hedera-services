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

package com.swirlds.common.threading.locks.locked;

/**
 * Provides access to the resource that has been locked
 *
 * @param <T>
 * 		the type of resource
 */
public interface LockedResource<T> extends Locked {
    /**
     * @return the locked resource, may be null
     */
    T getResource();

    /**
     * Sets the resource
     *
     * @param resource
     * 		the object to set
     */
    void setResource(T resource);
}
