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
package com.swirlds.common.utility;

/** An object wrapper for an int that allows it to mutate, unlike {@link Integer}. */
public class IntReference {
    /** the int value */
    private int value;

    /**
     * @param value the initial value of the int
     */
    public IntReference(final int value) {
        this.value = value;
    }

    /**
     * @return the int value
     */
    public int get() {
        return value;
    }

    /**
     * Set the value of the int
     *
     * @param value the value to set to
     */
    public void set(final int value) {
        this.value = value;
    }

    /** Increment the value by 1 */
    public void increment() {
        this.value++;
    }

    /** Decrement the value by 1 */
    public void decrement() {
        this.value--;
    }

    /**
     * Check if the supplied value is equal to the stored one.
     *
     * @param value the value to check
     * @return true if the values are equal
     */
    public boolean equalsInt(final int value) {
        return this.value == value;
    }
}
