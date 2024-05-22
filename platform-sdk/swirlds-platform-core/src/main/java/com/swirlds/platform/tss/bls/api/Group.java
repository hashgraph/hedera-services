/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.tss.bls.api;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents a mathematical group used in the Boneh-Lynn-Shacham (BLS) cryptographic scheme.
 *
 * <p>A group in this context is a set of elements combined with an operation that satisfies
 * the group properties: closure, associativity, identity, and invertibility. When the group
 * also satisfies the commutativity property, it is referred to as an abelian group.</p>
 *
 * <p>This class provides methods to obtain elements belonging to the group represented by the instance.
 *
 * @see GroupElement
 */
public interface Group extends UnderCurve {
    /**
     * Returns the group's generator
     *
     * @return the group's generator
     */
    @NonNull
    GroupElement getGenerator();

    /**
     * Creates a new group element with value 1
     *
     * @return the new group element
     */
    @NonNull
    GroupElement oneElement();

    /**
     * Creates a group element from a seed (32 bytes)
     *
     * @param seed the seed to generate the element from
     * @return the new group element
     */
    @NonNull
    GroupElement randomElement(byte[] seed);

    /**
     * Creates a group element from a seed (32 bytes)
     *
     * @param seed the seed to generate the element from
     * @return the new group element
     */
    @NonNull
    GroupElement randomElement();

    /**
     * Hashes an unbounded length input to a group element
     *
     * @param input the input to be hashes
     * @return the new group element
     */
    @NonNull
    GroupElement elementFromHash(byte[] input);

    /**
     * Multiplies a collection of group elements together
     *
     * @param elements the collection of elements to multiply together
     * @return a new group element which is the product the collection of elements
     */
    @NonNull
    GroupElement batchMultiply(@NonNull GroupElement elements);

    /**
     * Creates a group element from its serialized encoding
     *
     * @param bytes serialized form
     * @return the new group element, or null if construction failed
     */
    @NonNull
    GroupElement elementFromBytes(byte[] bytes);

    /**
     * Gets the size in bytes of a compressed group element
     *
     * @return the size of a compressed group element
     */
    int getCompressedSize();

    /**
     * Gets the size in bytes of an uncompressed group element
     *
     * @return the size of an uncompressed group element
     */
    int getUncompressedSize();

    /**
     * Gets the size in bytes of the seed necessary to generate a new element
     *
     * @return the size of a seed needed to generate a new element
     */
    int getSeedSize();
}
