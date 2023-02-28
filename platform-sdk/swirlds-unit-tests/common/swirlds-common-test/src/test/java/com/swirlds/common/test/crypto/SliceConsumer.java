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

package com.swirlds.common.test.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

/**
 * Provides a functional interface for lambda methods that operate on a slice of a given array.
 *
 * @param <T>
 * 		the array type
 * @param <U>
 * 		the offset type
 * @param <V>
 * 		the length type
 */
@FunctionalInterface
public interface SliceConsumer<T, U, V> {

    /**
     * Processes a given slice of the array, starting with the element at offset and ending with the element at ((offset
     * + length) - 1).
     *
     * @param items
     * 		the array of elements
     * @param offset
     * 		the beginning offset where processing should begin
     * @param length
     * 		the total number of elements starting from the offset that should be processed
     */
    void accept(final T[] items, final U offset, final V length)
            throws NoSuchProviderException, NoSuchAlgorithmException;
}
