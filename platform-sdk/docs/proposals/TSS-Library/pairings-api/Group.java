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


package com.hedera.cryptography.pairings.api;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;

/**
 * Represents a mathematical group used in a pairing-based cryptography system.
 *
 * <p>A group in this context is a set of elements combined with an operation that satisfies
 * the group properties: closure, associativity, identity, and invertibility. When the group
 * also satisfies the commutativity property, it is referred to as an abelian group.</p>
 *
 * <p>This class provides methods to obtain elements belonging to the group represented by the instance.
 *
 * @see GroupElement
 */
public interface Group {
    /**
     * Returns the opposite group of this group
     * <p>
     * If this group is G₁, then the opposite group is G₂, and vice versa.
     *
     * @return the opposite group
     */
    @NonNull
    default Group getOppositeGroup() {
        return getPairing().getOtherGroup(this);
    }

    /**
     * Returns the pairing associated with this group
     *
     * @return the pairing associated with this group
     */
    @NonNull
    BilinearPairing getPairing();

    /**
     * Returns the group's generator
     *
     * @return the group's generator
     */
    @NonNull
    GroupElement getGenerator();

    /**
     * Creates a new group element with value 0
     *
     * @return the new group element
     */
    @NonNull
    GroupElement zeroElement();

    /**
     * Creates a group element from a seed
     *
     * @param seed the seed to generate the element from
     * @return the new group element
     */
    @NonNull
    GroupElement randomElement(@NonNull byte[] seed);

    /**
     * Hashes an unbounded length input to a group element
     *
     * @param input the input to be hashed
     * @return the new group element
     */
    @NonNull
    GroupElement elementFromHash(@NonNull byte[] input);

    /**
     * Adds a collection of group elements together
     *
     * @param elements the collection of elements to add together
     * @return a new group element which is the sum the collection of elements
     */
    @NonNull
    GroupElement batchAdd(@NonNull Collection<GroupElement> elements);

    /**
     * Creates a group element from its serialized encoding
     *
     * @param bytes serialized form
     * @return the new group element, or null if construction failed
     */
    @NonNull
    GroupElement elementFromBytes(@NonNull byte[] bytes);

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
