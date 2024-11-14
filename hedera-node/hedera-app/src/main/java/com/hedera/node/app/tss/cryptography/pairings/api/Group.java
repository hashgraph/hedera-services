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

package com.hedera.node.app.tss.cryptography.pairings.api;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Collection;
import java.util.Random;

/**
 * Represents a mathematical group used in a pairing-based cryptography system.
 *
 * <p>A group in this context is a set of elements (curve points) with operations that satisfies the group properties:
 *  closure, associativity, identity, and invertibility.
 * <p>Curves can be defined by more than one group.
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
        return getPairingFriendlyCurve().getOtherGroup(this);
    }

    /**
     * Returns the pairing associated with this group
     *
     * @return the pairing associated with this group
     */
    @NonNull
    PairingFriendlyCurve getPairingFriendlyCurve();

    /**
     * Returns the group's generator.
     * A generator is a point that when multiplied to every different scalar value, it can produce all other elements of the group.
     *
     * @return the group's generator
     */
    @NonNull
    GroupElement generator();

    /**
     * Creates a new group element with value 0
     *
     * @return the new group element
     */
    @NonNull
    GroupElement zero();

    /**
     * Creates a group element from a rng
     *
     * @param random the rng to use
     * @return the new group element
     */
    @NonNull
    default GroupElement random(Random random) {
        byte[] seed = new byte[this.seedSize()];
        random.nextBytes(seed);
        return random(seed);
    }

    /**
     * Creates a group element from a seed
     *
     * @param seed the seed to generate the element from
     * @return the new group element
     */
    @NonNull
    GroupElement random(@NonNull byte[] seed);

    /**
     * Hashes an unbounded length input to a group element
     *
     * @param input the input to be hashed
     * @return the new group element
     */
    @NonNull
    GroupElement hashToCurve(@NonNull byte[] input);

    /**
     * Adds a collection of group elements together
     *
     * @param elements the collection of elements to add together
     * @return a new group element which is the sum the collection of elements
     */
    @NonNull
    GroupElement add(@NonNull Collection<GroupElement> elements);

    /**
     * Creates a group element from its serialized encoding
     *
     * @param bytes serialized form
     * @return the new group element
     * @throws IllegalArgumentException if the byte representation is not a valid point on the curve
     */
    @NonNull
    GroupElement fromBytes(@NonNull byte[] bytes);

    /**
     * Gets the size in bytes of the seed necessary to generate a new element
     *
     * @return the size of a seed needed to generate a new element
     */
    int seedSize();

    /**
     * Gets the size in bytes of a group element
     *
     * @return the size in bytes of a group element
     */
    int elementSize();
}
