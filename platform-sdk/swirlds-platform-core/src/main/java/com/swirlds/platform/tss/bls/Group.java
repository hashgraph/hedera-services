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

package com.swirlds.platform.tss.bls;

import java.util.Collection;

/**
 * Interface representing a generic group
 *
 * <p>This is a factory interface, responsible for creating {@link GroupElement group elements}
 *
 * TODO: this is a temporary placeholder, until we have the BLS library ready for use
 */
public interface Group {
    /**
     * Creates a new group element with value 1
     *
     * @return the new group element
     */
    GroupElement oneElement();

    /** Creates a group element from a seed (32 bytes) */
    GroupElement randomElement(byte[] seed);

    /**
     * Hashes an unbounded length input to a group element
     *
     * @param input the input to be hashes
     * @return the new group element
     */
    GroupElement hashToGroup(byte[] input);

    /**
     * Multiplies a collection of group elements together
     *
     * @param elements the collection of elements to multiply together
     * @return a new group element which is the product the collection of elements
     */
    GroupElement batchMultiply(Collection<GroupElement> elements);

    /**
     * Creates a group element from its serialized encoding
     *
     * @param bytes serialized form
     * @return the new group element, or null if construction failed
     */
    GroupElement deserializeElementFromBytes(byte[] bytes);

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
