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

package com.swirlds.pairings.bls12381.impl.jni;

import com.swirlds.nativesupport.LibraryLoader;
import com.swirlds.pairings.bls12381.impl.Bls12381FieldElement;
import com.swirlds.pairings.bls12381.impl.Bls12381Group1Element;
import com.swirlds.pairings.bls12381.impl.Bls12381Group2Element;

/** Class containing definitions for native rust functions that operate on the g1 group */
public final class Bls12381Bindings {

    /**
     * The name of the compiled BLS library
     */
    private static final String LIBRARY_NAME = "swirlds-parings-bls12381-impl";

    static {
        LibraryLoader.loadBundledLibrary(Bls12381Bindings.class, LIBRARY_NAME);
    }

    /** The code returned from the rust interface if a call succeeds */
    public static final int SUCCESS = 0;

    /** Hidden constructor */
    private Bls12381Bindings() {}

    /**
     * Creates a new identity element of the g1 group
     *
     * @param output the byte array that will be filled with the new group element
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int newG1Identity(final byte[] output);

    /**
     * Creates a new random element of the g1 group, from a byte array seed
     *
     * @param inputSeed the seed to create the new group element with
     * @param output the byte array that will be filled with the new group element
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int newRandomG1(final byte[] inputSeed, final byte[] output);

    /**
     * Checks if 2 elements of the g1 group are equal
     *
     * @param element1 the first g1 group element
     * @param element2 the second g1 group element
     * @return true if the elements are equal, otherwise false
     */
    public static native boolean g1ElementEquals(
            final Bls12381Group1Element element1, final Bls12381Group1Element element2);

    /**
     * Checks whether a g1 element is valid
     *
     * @param element the element being checked for validity
     * @return true if the element is valid, otherwise false
     */
    public static native boolean checkG1Validity(final Bls12381Group1Element element);

    /**
     * Computes the quotient of 2 elements of the g1 group
     *
     * @param element1 the first group element
     * @param element2 the second group element
     * @param output the byte array that will be filled with the quotient group element
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int g1Divide(
            final Bls12381Group1Element element1, final Bls12381Group1Element element2, final byte[] output);

    /**
     * Computes the product of 2 elements of the g1 group
     *
     * @param element1 the first group element
     * @param element2 the second group element
     * @param output the byte array that will be filled with the product group element
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int g1Multiply(
            final Bls12381Group1Element element1, final Bls12381Group1Element element2, final byte[] output);

    /**
     * Computes the product of a batch of elements
     *
     * @param elementBatch the batch of elements to multiply together
     * @param output the byte array that will be filled with the product group element
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int g1BatchMultiply(final Bls12381Group1Element[] elementBatch, final byte[] output);

    /**
     * Computes the value of a g1 group element, taken to the power of a scalar
     *
     * @param base an element of the g1 group
     * @param exponent the scalar exponent
     * @param output the byte array that will be filled with the new result group element
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int g1PowZn(
            final Bls12381Group1Element base, final Bls12381FieldElement exponent, final byte[] output);

    /**
     * Compresses a g1 element
     *
     * @param element the element to compress
     * @param output the byte array that will be filled with the compressed group element
     * @return a compressed version of the element
     */
    public static native int g1Compress(final Bls12381Group1Element element, final byte[] output);

    /**
     * Creates a new identity element of the g2 group
     *
     * @param output the byte array that will be filled with the new group element
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int newG2Identity(final byte[] output);

    /**
     * Creates a new random element of the g2 group, from a byte array seed
     *
     * @param inputSeed the seed to create the new group element with
     * @param output the byte array that will be filled with the new group element
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int newRandomG2(final byte[] inputSeed, final byte[] output);

    /**
     * Checks if 2 elements of the g2 group are equal
     *
     * @param element1 the first g2 group element
     * @param element2 the second g2 group element
     * @return true if the elements are equal, otherwise false
     */
    public static native boolean g2ElementEquals(
            final Bls12381Group2Element element1, final Bls12381Group2Element element2);

    /**
     * Checks whether a g2 element is valid
     *
     * @param element the element being checked for validity
     * @return true if the element is valid, otherwise false
     */
    public static native boolean checkG2Validity(final Bls12381Group2Element element);

    /**
     * Computes the quotient of 2 elements of the g2 group
     *
     * @param element1 the first group element
     * @param element2 the second group element
     * @param output the byte array that will be filled with the quotient group element
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int g2Divide(
            final Bls12381Group2Element element1, final Bls12381Group2Element element2, final byte[] output);

    /**
     * Computes the product of 2 elements of the g2 group
     *
     * @param element1 the first group element
     * @param element2 the second group element
     * @param output the byte array that will be filled with the product group element
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int g2Multiply(
            final Bls12381Group2Element element1, final Bls12381Group2Element element2, final byte[] output);

    /**
     * Computes the product of a batch of elements
     *
     * @param elementBatch the batch of elements to multiply together
     * @param output the byte array that will be filled with the product group element
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int g2BatchMultiply(final Bls12381Group2Element[] elementBatch, final byte[] output);

    /**
     * Computes the value of a g2 group element, taken to the power of a scalar
     *
     * @param base an element of the g2 group
     * @param exponent the scalar exponent
     * @param output the byte array that will be filled with the new result group element
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int g2PowZn(
            final Bls12381Group2Element base, final Bls12381FieldElement exponent, final byte[] output);

    /**
     * Compresses a g2 element
     *
     * @param element the element to compress
     * @param output the byte array that will be filled with the compressed group element
     * @return a compressed version of the element
     */
    public static native int g2Compress(final Bls12381Group2Element element, final byte[] output);

    /**
     * Creates a new random scalar from a seed value
     *
     * @param inputSeed the byte seed to be used to create the new scalar
     * @param output the byte array that will be filled with the new scalar
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int newRandomScalar(final byte[] inputSeed, final byte[] output);

    /**
     * Creates a new scalar from a long
     *
     * @param inputLong the long to be used to create the new scalar
     * @param output the byte array that will be filled with the new scalar
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int newScalarFromLong(final long inputLong, final byte[] output);

    /**
     * Creates a new zero value scalar
     *
     * @param output the byte array that will be filled with the new scalar
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int newZeroScalar(final byte[] output);

    /**
     * Creates a new one value scalar
     *
     * @param output the byte array that will be filled with the new scalar
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int newOneScalar(final byte[] output);

    /**
     * Checks whether 2 scalar values are equal
     *
     * @param scalar1 the first scalar value
     * @param scalar2 the second scalar value
     * @return true if the scalars are equal, otherwise false
     */
    public static native boolean scalarEquals(final Bls12381FieldElement scalar1, final Bls12381FieldElement scalar2);

    /**
     * Checks whether a scalar is valid
     *
     * @param scalar the scalar being checked for validity
     * @return true if the scalar is valid, otherwise false
     */
    public static native boolean checkScalarValidity(final Bls12381FieldElement scalar);

    /**
     * Computes the sum of 2 scalar values
     *
     * @param scalar1 the first scalar value
     * @param scalar2 the second scalar value
     * @param output the byte array that will be filled with the new scalar
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int scalarAdd(
            final Bls12381FieldElement scalar1, final Bls12381FieldElement scalar2, final byte[] output);

    /**
     * Computes the difference between 2 scalar values
     *
     * @param scalar1 the first scalar value
     * @param scalar2 the second scalar value
     * @param output the byte array that will be filled with the new scalar
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int scalarSubtract(
            final Bls12381FieldElement scalar1, final Bls12381FieldElement scalar2, final byte[] output);

    /**
     * Computes the product of 2 scalar values
     *
     * @param scalar1 the first scalar value
     * @param scalar2 the second scalar value
     * @param output the byte array that will be filled with the new scalar
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int scalarMultiply(
            final Bls12381FieldElement scalar1, final Bls12381FieldElement scalar2, final byte[] output);

    /**
     * Computes the quotient of 2 scalar values
     *
     * @param scalar1 the first scalar value
     * @param scalar2 the second scalar value
     * @param output the byte array that will be filled with the new scalar
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int scalarDivide(
            final Bls12381FieldElement scalar1, final Bls12381FieldElement scalar2, final byte[] output);

    /**
     * Computes the value a scalar to the power of a big integer
     *
     * @param base a scalar value
     * @param exponent a big integer
     * @param output the byte array that will be filled with the new scalar
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int scalarPower(final Bls12381FieldElement base, final byte[] exponent, final byte[] output);

    /**
     * Computes 2 separate pairings, A and B, and checks the resulting elements for equality
     *
     * @param g1a the g1 group element for pairing A
     * @param g2a the g2 group element for pairing A
     * @param g1b the g1 group element for pairing B
     * @param g2b the g2 group element for pairing B
     * @return true if the pairings are equal, otherwise false
     */
    public static native boolean comparePairing(
            final Bls12381Group1Element g1a,
            final Bls12381Group2Element g2a,
            final Bls12381Group1Element g1b,
            final Bls12381Group2Element g2b);

    /**
     * Computes a pairing, and gets a byte representation of the result
     *
     * @param g1 the g1 group element for the pairing
     * @param g2 the g2 group element for the pairing
     * @param output the byte array that will be filled with the pairing byte representation
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public static native int pairingDisplay(
            final Bls12381Group1Element g1, final Bls12381Group2Element g2, final byte[] output);
}
