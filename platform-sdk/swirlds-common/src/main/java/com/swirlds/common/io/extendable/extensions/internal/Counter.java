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

package com.swirlds.common.io.extendable.extensions.internal;

/**
 * An interface for a counter
 */
public interface Counter {

    /**
     * Resets the count to 0
     */
    void resetCount();

    /**
     * get the current count
     *
     * @return the current count
     */
    long getCount();

    /**
     * Returns the current count and resets it to 0
     *
     * @return the count before the reset
     */
    long getAndResetCount();

    /**
     * Adds the specified value to the count
     *
     * @param value
     * 		the value to be added
     * @return the new count
     */
    long addToCount(long value);
}
