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
import java.math.BigInteger;
import java.util.List;
import java.util.Random;

/**
 * Represents a finite field used in a pairing-based cryptography scheme.
 *  <p>A finite field is a ring of scalars values [0,1,...r-1, 0, 1, ...] where all operations are done modulus a prime number P.
 * <p> All operations inside the field, produce a value that belongs to it.
 * In an elliptic curve, these are the values that can be used to operate in certain {@link Group} operations.
 * <p>Each scalar element in the field is a {@link FieldElement}.
 *
 * @see Group
 * @see FieldElement
 */
public interface Field {
    /**
     * Creates a random field element
     *
     * @param random the source of randomness
     * @return the random field element
     */
    @NonNull
    default FieldElement random(@NonNull final Random random) {
        final byte[] seed = new byte[seedSize()];
        random.nextBytes(seed);

        return random(seed);
    }

    /**
     * Creates a new field element from a non-negative long value.
     * If {@code inputLong} is larger than the modulus p of this Field, this method performs the appropriate reduction.
     *
     * @param inputLong the non-negative long to use to create the field element
     * @return the new field element
     * @throws IllegalArgumentException if the value is negative
     */
    @NonNull
    FieldElement fromLong(long inputLong);

    /**
     * Creates a field element from a seed
     *
     * @param seed a seed to use to generate randomness
     * @return the new field element
     */
    @NonNull
    FieldElement random(@NonNull byte[] seed);

    /**
     * Creates a field element from its serialized encoding
     *
     * @param bytes serialized form
     * @return the new field element
     */
    @NonNull
    FieldElement fromBytes(@NonNull byte[] bytes);

    /**
     * Creates a field element from a non-negative {@link BigInteger} value.
     * If {@code bigInteger} is larger than the modulus p of this Field, this method performs the appropriate reduction.
     *
     * @param bigInteger the non-negative scalar
     * @return the new field element
     * @throws IllegalArgumentException if the value is negative
     */
    @NonNull
    FieldElement fromBigInteger(@NonNull BigInteger bigInteger);

    /**
     * Gets the size in bytes of an element
     *
     * @return the size of an element
     */
    int elementSize();

    /**
     * Gets the size in bytes of the seed necessary to generate a new element
     *
     * @return the size of a seed needed to generate a new element
     */
    int seedSize();

    /**
     * Get the pairing that this field is used in
     *
     * @return the pairing
     */
    @NonNull
    PairingFriendlyCurve getPairingFriendlyCurve();

    /**
     * Resolves the addition of multiple field elements
     * @param fieldElements a list of fieldElements
     * @return a field element that is the addition of all the field elements in the list
     */
    // FUTURE implement this with rust in task #16096
    default FieldElement add(@NonNull final List<FieldElement> fieldElements) {
        FieldElement accum = this.fromLong(0);
        for (FieldElement fieldElement : fieldElements) {
            accum = accum.add(fieldElement);
        }
        return accum;
    }
}
