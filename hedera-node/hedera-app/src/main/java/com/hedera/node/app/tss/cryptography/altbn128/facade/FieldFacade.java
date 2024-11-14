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

package com.hedera.node.app.tss.cryptography.altbn128.facade;

import com.hedera.node.app.tss.cryptography.altbn128.AltBn128Exception;
import com.hedera.node.app.tss.cryptography.altbn128.adapter.FieldElementsLibraryAdapter;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Objects;

import static com.hedera.node.app.tss.cryptography.altbn128.adapter.FieldElementsLibraryAdapter.SUCCESS;

/**
 * This class acts as a facade that simplifies the interaction for operating with {@code FieldElement} {@code byte[]} representations.
 *  It abstracts the complexities of dealing with return codes and input and output parameters
 *  providing a higher-level interface easier to interact with from Java.
 **/
public final class FieldFacade {

    /** the underlying library adapter  */
    private final FieldElementsLibraryAdapter adapter;
    /** the occupied size in bytes of this of the fieldElements representations. */
    private final int size;
    /** the occupied size in bytes of the random seed.  */
    private final int randomSeedSize;

    /**
     * Creates an instance of this facade.
     * @param fieldElementsLibraryAdapter the adapter containing the underlying logic.
     */
    public FieldFacade(@NonNull final FieldElementsLibraryAdapter fieldElementsLibraryAdapter) {
        this.adapter = Objects.requireNonNull(fieldElementsLibraryAdapter, "adapter must not be null");
        // Caching the value given that this is frequently called
        this.size = adapter.fieldElementsSize();
        // Caching the value given that this is frequently called
        this.randomSeedSize = adapter.randomSeedSize();
    }

    /**
     * Creates a byte array representation of a fieldElement form a {@code long} parameter.
     * @param inputLong the long parameter to create the representation from. Must be positive or zero.
     * @return a byte array of size {@link FieldFacade#size()} with the representation of the input
     * @throws AltBn128Exception in case of error
     */
    public byte[] fromLong(final long inputLong) {
        if (inputLong < 0) {
            throw new IllegalArgumentException("input long must not be negative");
        }
        final byte[] output = new byte[size];
        final int result = adapter.fieldElementsFromLong(inputLong, output);
        if (result != SUCCESS) {
            throw new AltBn128Exception(result, "fieldElementFromLong");
        }
        return output;
    }

    /**
     * Creates a byte array representation of a fieldElement form randomly generated seed of size {@link FieldFacade#randomSeedSize()}
     * @param seed the randomly generated seed.
     * @return a byte array representation of a fieldElement form seed
     * @throws NullPointerException if the seed is null
     * @throws IllegalArgumentException if the seed is of invalid size
     * @throws AltBn128Exception in case of error
     */
    public byte[] fromRandomSeed(@NonNull final byte[] seed) {
        if (Objects.requireNonNull(seed, "Seed must not be null").length != randomSeedSize) {
            throw new IllegalArgumentException("Invalid random seed");
        }
        final byte[] output = new byte[size];
        final int result = adapter.fieldElementsFromRandomSeed(seed, output);
        if (result != SUCCESS) {
            throw new AltBn128Exception(result, "fieldElementFromRandomSeed");
        }
        return output;
    }

    /**
     * Creates a byte array representation of a fieldElement form another byte array representation
     * TODO: review if this is necessary, what validation is being provided.
     * @param representation the byte representation to validate
     * @return a byte array representation of a fieldElement form the provided byte array representation
     * @throws NullPointerException if the representation is null
     * @throws IllegalArgumentException if the representation is invalid
     * @throws AltBn128Exception in case of error
     */
    public byte[] fromBytes(@NonNull final byte[] representation) {
        if (Objects.requireNonNull(representation, "representation must not be null").length != size) {
            throw new IllegalArgumentException("Invalid byte[] representation");
        }
        final byte[] output = new byte[size];
        final int result = adapter.fieldElementsFromBytes(representation, output);
        if (result != SUCCESS) {
            throw new AltBn128Exception(result, "fieldElementFromBytes");
        }
        return output;
    }

    /**
     * Return a byte array representation of a fieldElement of value 0.
     * @return a byte array of size {@link FieldFacade#size()} with the representation of the element 0
     * @throws AltBn128Exception in case of error
     */
    public byte[] zero() {
        final byte[] output = new byte[size];
        final int result = adapter.fieldElementsZero(output);
        if (result != SUCCESS) {
            throw new AltBn128Exception(result, "fieldElementsZero");
        }
        return output;
    }

    /**
     * Return a byte array representation of a fieldElement of value 1.
     * @return a byte array of size {@link FieldFacade#size()} with the representation of the element 1
     * @throws AltBn128Exception in case of error
     */
    public byte[] one() {
        final byte[] output = new byte[size];
        final int result = adapter.fieldElementsOne(output);
        if (result != SUCCESS) {
            throw new AltBn128Exception(result, "fieldElementsOne");
        }
        return output;
    }

    /**
     * Returns {@code true} if {@code value} and {@code other} are equals. {@code false} otherwise
     * @param value first value to compare
     * @param other second value to compare
     * @return {@code true} if {@code value} and {@code other} are equals. {@code false} otherwise
     * @throws AltBn128Exception in case of error
     */
    public boolean equals(byte[] value, byte[] other) {
        final int result = adapter.fieldElementsEquals(value, other);
        if (result < SUCCESS) {
            throw new AltBn128Exception(result, "fieldElementsEquals");
        }
        return result == 1;
    }

    /**
     * Return the occupied size in bytes of the fieldElements representations.
     * @return the occupied size in bytes of the fieldElements representations.
     */
    public int size() {
        return this.size;
    }

    /**
     * Return the occupied size in bytes of the random seed.
     * @return the size in bytes for the random seed.
     */
    public int randomSeedSize() {
        return this.randomSeedSize;
    }

    /**
     * adds two field elements representations
     * @param value first value
     * @param other second value
     * @return {@code true} if {@code value} and {@code other} are equals. {@code false} otherwise
     * @throws AltBn128Exception in case of error
     */
    public byte[] add(@NonNull final byte[] value, @NonNull final byte[] other) {
        final byte[] output = new byte[size];
        final int result = adapter.fieldElementsAdd(value, other, output);
        if (result < SUCCESS) {
            throw new AltBn128Exception(result, "fieldElementsAdd");
        }
        return output;
    }

    /**
     * Multiplies two field elements representations
     * @param value first value
     * @param other second value
     * @return a byte array of size {@link FieldFacade#size()} with the representation of the result of the operation
     * @throws AltBn128Exception in case of error
     */
    public byte[] multiply(@NonNull final byte[] value, @NonNull final byte[] other) {
        final byte[] output = new byte[size];
        final int result = adapter.fieldElementsMultiply(value, other, output);
        if (result < SUCCESS) {
            throw new AltBn128Exception(result, "fieldElementsMultiply");
        }
        return output;
    }
    /**
     * Subtracts two field elements representations
     * @param value first value
     * @param other second value
     * @return a byte array of size {@link FieldFacade#size()} with the representation of the result of the operation
     * @throws AltBn128Exception in case of error
     */
    public byte[] subtracts(@NonNull final byte[] value, @NonNull final byte[] other) {
        final byte[] output = new byte[size];
        final int result = adapter.fieldElementsSubtract(value, other, output);
        if (result < SUCCESS) {
            throw new AltBn128Exception(result, "fieldElementsSubtracts");
        }
        return output;
    }

    /**
     * Multiplies two field elements representations
     * @param value first value
     * @return a byte array of size {@link FieldFacade#size()} with the representation of the result of the operation
     * @throws AltBn128Exception in case of error
     */
    public byte[] inverse(@NonNull final byte[] value) {
        final byte[] output = new byte[size];
        final int result = adapter.fieldElementsInverse(value, output);
        if (result == FieldElementsLibraryAdapter.CANNOT_INVERT) {
            throw new IllegalArgumentException("The scalar cannot be inverted");
        }
        if (result < SUCCESS) {
            throw new AltBn128Exception(result, "fieldElementsMultiply");
        }
        return output;
    }

    /**
     * Multiplies two field elements representations
     * @param value the base scalar value
     * @param exponent the long parameter to create the representation from
     * @return a byte array of size {@link FieldFacade#size()} with the representation of the result of the operation
     * @throws AltBn128Exception in case of error
     */
    public byte[] pow(@NonNull final byte[] value, final long exponent) {
        if (exponent < 0) throw new IllegalArgumentException("exponent must not be negative");
        final byte[] output = new byte[size];
        final int result = adapter.fieldElementsPow(value, exponent, output);
        if (result < SUCCESS) {
            throw new AltBn128Exception(result, "fieldElementsPow");
        }
        return output;
    }
}
