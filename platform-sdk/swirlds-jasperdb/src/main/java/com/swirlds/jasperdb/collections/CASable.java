/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.jasperdb.collections;

/**
 * An interface for classes that can be used in Compare-And-Swap operations.
 * Implementation can use atomic CAS or straightforward logic in single-threaded case.
 */
public interface CASable {

    /**
     * Read current long value at index
     * @param index
     *      position, key, etc.
     * @return
     *      read value
     */
    long get(final long index);

    /**
     * Updates the element at index to newValue if the element's current value is equal to oldValue.
     * @param index
     *      position, key, etc.
     * @param oldValue
     *      expected value to be overwritten
     * @param newValue
     *      new value to replace the expected value
     * @return
     *      true if successful, false if the current value was not equal to the expected value
     */
    boolean putIfEqual(final long index, final long oldValue, final long newValue);
}
