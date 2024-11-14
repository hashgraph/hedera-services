/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.tss.cryptography.altbn128.adapter;

/**
 * This interface defines a contract and any third party library that provides the functionality for handling Bilinear Pairings operations must adhere to.
 *
 *  @apiNote This contract is not Java friendly, and it is defined in a way that is easy to implement in other languages.
 *  All operations return a status code, where 0 mean success, and a non-zero result means a codified error callers must know how to deal with.
 *  As the native code does not guarantee validation of parameters, Input and output parameters must be provided and instantiated accordingly for the invocation to be performed safety.
 *  i.e.:Sending non-null values and correctly instantiated arrays (expected size) is responsibility of the caller.
 */
public interface PairingsLibraryAdapter {

    /**
     * returns if the result of the pairings operation between the first two points is equals to the result of the pairings operation between the second two
     * {@code value2} and {@code value4} should belong to opposite groups than {@code value1} {@code value3}
     *
     * @param value1 a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} that contains the internal represents the point
     * @param value2 a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} that contains the internal represents the point
     * @param value3 a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} that contains the internal represents the point
     * @param value4 a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} that contains the internal represents the point
     * @return 0 if false, 1 if true, or a less than zero error code if there was an error
     */
    int pairingsEquals(final byte[] value1, final byte[] value2, final byte[] value3, final byte[] value4);
}
