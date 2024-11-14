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

package com.hedera.node.app.tss.cryptography.altbn128.adapter.jni;

import com.hedera.node.app.tss.cryptography.altbn128.adapter.FieldElementsLibraryAdapter;
import com.hedera.node.app.tss.cryptography.altbn128.adapter.GroupElementsLibraryAdapter;
import com.hedera.node.app.tss.cryptography.altbn128.adapter.PairingsLibraryAdapter;
import com.hedera.node.app.tss.cryptography.nativesupport.SingletonLoader;

/**
 * This class serves as an adapter between the Java code and the native arkworks altBn128 Rust functions.
 **/
public final class ArkBn254Adapter
        implements FieldElementsLibraryAdapter, GroupElementsLibraryAdapter, PairingsLibraryAdapter {
    /** Instance Holder for lazy loading and concurrency handling */
    private static final SingletonLoader<ArkBn254Adapter> INSTANCE_HOLDER =
            new SingletonLoader<>("bn254", new ArkBn254Adapter());

    static {
        // Open the package to allow access to the native library
        // This can be done in module-info.java as well, but by default the compiler complains since there are no
        // classes in the package, just resources
        ArkBn254Adapter.class
                .getModule()
                .addOpens(INSTANCE_HOLDER.getNativeLibraryPackageName(), SingletonLoader.class.getModule());
    }

    private ArkBn254Adapter() {
        // private constructor to ensure singleton
    }

    /**
     * Returns the singleton instance of this library adapter.
     *
     * @return the singleton instance of this library adapter.
     */
    public static ArkBn254Adapter getInstance() {
        return INSTANCE_HOLDER.getInstance();
    }

    /**
     * Returns the byte size of the random seed to use.
     *
     * @return the byte size of the random seed to use.
     */
    public native int randomSeedSize();

    /**
     * Creates a new random scalar from a seed value
     *
     * @param inputSeed the byte seed to be used to create the new scalar
     * @param output    the byte array that will be filled with the new scalar
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    public native int fieldElementsFromRandomSeed(final byte[] inputSeed, final byte[] output);

    /**
     * Creates a new scalar from a long
     *
     * @param inputLong the long to be used to create the new scalar
     * @param output    the byte array that will be filled with the new scalar
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    public native int fieldElementsFromLong(final long inputLong, final byte[] output);

    /**
     * Creates a new scalar from a byte[]
     *
     * @param input  the that represents the scalar
     * @param output the byte array that will be filled with the new scalar
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    public native int fieldElementsFromBytes(final byte[] input, final byte[] output);

    /**
     * Creates a new zero value scalar
     *
     * @param output the byte array that will be filled with the new scalar
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    public native int fieldElementsZero(final byte[] output);

    /**
     * Creates a new one value scalar.
     *
     * @param output the byte array that will be filled with the new scalar
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    public native int fieldElementsOne(final byte[] output);

    /**
     * returns if two representations are the same
     *
     * @param value the that represents a scalar
     * @param other the that represents another scalar
     * @return 0 if false, 1 if true, or a less than zero error code if there was an error
     */
    public native int fieldElementsEquals(final byte[] value, final byte[] other);

    /**
     * Returns the byte size of a field element object.
     *
     * @return a non-zero error code if there was an error, otherwise 0
     */
    public native int fieldElementsSize();

    /**
     * Adds two scalars
     *
     * @param input1  the that represents the scalar
     * @param input2  the that represents the scalar
     * @param output the byte array that will be filled with the result of the operation
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    public native int fieldElementsAdd(final byte[] input1, final byte[] input2, final byte[] output);

    /**
     * subtracts two scalars
     *
     * @param input1  the that represents the scalar
     * @param input2  the that represents the scalar
     * @param output the byte array that will be filled with the result of the operation
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    public native int fieldElementsSubtract(final byte[] input1, final byte[] input2, final byte[] output);

    /**
     * Multiplies two scalars
     *
     * @param input1  the that represents the scalar
     * @param input2  the that represents the scalar
     * @param output the byte array that will be filled with the result of the operation
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    public native int fieldElementsMultiply(final byte[] input1, final byte[] input2, final byte[] output);

    /**
     * Creates powers a scalar to an exponent
     * @param input1  the that represents the scalar
     * @param exponent the long to be used to create the new scalar
     * @param output the byte array that will be filled with the result of the operation
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    public native int fieldElementsPow(final byte[] input1, final long exponent, final byte[] output);

    /**
     * returns the inverse scalar (value^-1)
     *
     * @param input  the that represents the scalar
     * @param output the byte array that will be filled with the result of the operation
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    public native int fieldElementsInverse(final byte[] input, final byte[] output);

    /**
     * Creates a GroupElement byte internal representation from a seed byte array
     * @param group on which of the groups of the curve to perform the operation
     * @param input a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} that contains the internal represents the point
     * @param output a {@link GroupElementsLibraryAdapter#groupElementsSize(int)} array to hold the internal representation of the point
     * @return {@link GroupElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    public native int groupElementsFromSeed(final int group, final byte[] input, final byte[] output);

    /**
     * Attempts to obtain a GroupElement byte internal representation from a given x coordinate
     *
     * @param group  on which of the groups of the curve to perform the operation
     * @param input  a 256-bit byte array of that represents an x coordinate
     * @param output a {@link GroupElementsLibraryAdapter#groupElementsSize(int)} array to hold the internal
     *               representation of the point, if the given x coordinate is not in the curve, the output will be
     *               unchanged
     * @return {@link GroupElementsLibraryAdapter#SUCCESS} for success, {@link GroupElementsLibraryAdapter#NOT_IN_CURVE}
     * if the point is not in the curve, or a less than zero error code if there was an error
     */
    public native int groupElementsFromXCoordinate(final int group, final byte[] input, final byte[] output);

    /**
     * Returns the GroupElement byte internal representation of the point at infinity
     * @param group on which of the groups of the curve to perform the operation
     * @param output a {@link GroupElementsLibraryAdapter#groupElementsSize(int)} array to hold the internal representation of the point
     * @return {@link GroupElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    public native int groupElementsZero(final int group, final byte[] output);

    /**
     * Returns the GroupElement byte internal representation of the generator point of the group
     * @param group on which of the groups of the curve to perform the operation
     * @param output a {@link GroupElementsLibraryAdapter#groupElementsSize(int)} array to hold the internal representation of the point
     * @return {@link GroupElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    public native int groupElementsGenerator(final int group, final byte[] output);

    /**
     * returns if two representations are the same
     *
     * @param group on which of the groups of the curve to perform the operation
     * @param value a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} that contains the internal represents the point
     * @param other a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)}  that contains the internal represents the point
     * @return 0 if false, 1 if true, or a less than zero error code if there was an error
     */
    public native int groupElementsEquals(final int group, final byte[] value, final byte[] other);

    /**
     * Returns the byte size of a groupElement internal representation.
     *
     * @param group on which of the groups of the curve to perform the operation
     * @return the byte size of a groupElement internal representation.
     */
    public native int groupElementsSize(final int group);

    /**
     * Returns the addition of code {@code value} and  {@code other}.
     *
     * @param group on which of the groups of the curve to perform the operation
     * @param value a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} that contains the internal represents the point
     * @param other a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} that contains the internal represents the point
     * @param output a {@link GroupElementsLibraryAdapter#groupElementsSize(int)} array to hold the internal representation of the point
     * @return 0 if false, 1 if true, or a less than zero error code if there was an error
     */
    public native int groupElementsAdd(final int group, final byte[] value, final byte[] other, final byte[] output);

    /**
     * Returns the scalar multiplication between code {@code point} and {@code scalar}.
     *
     * @param group on which of the groups of the curve to perform the operation
     * @param point a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} that will be used as the seed to create the point
     * @param scalar a byte array of {@link FieldElementsLibraryAdapter#fieldElementsSize()}} that contains the representation of the scalar
     * @param output a {@link GroupElementsLibraryAdapter#groupElementsSize(int)} array to hold the internal representation of the point
     * @return {@link GroupElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    public native int groupElementsScalarMul(
            final int group, final byte[] point, final byte[] scalar, final byte[] output);

    /**
     * Validates if a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} is a valid representation of a point in the curve
     *
     * @param group on which of the groups of the curve to perform the operation
     * @param point a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} that will be used as the seed to create the
     *              point
     * @return {@link GroupElementsLibraryAdapter#SUCCESS} for success, {@link GroupElementsLibraryAdapter#NOT_IN_CURVE} if the point is invalid or a less than zero error code if there was an error
     */
    public native int groupElementsBytes(final int group, final byte[] point);

    /**
     * Returns the result of the multiplication of the {@link GroupElementsLibraryAdapter#groupElementsGenerator(int, byte[])} for each scalar in the {@code scalars} list
     *
     * @param group on which of the groups of the curve to perform the operation
     * @param scalars a byte matrix representing a list of N byte arrays of {@link FieldElementsLibraryAdapter#fieldElementsSize()}} size each representing a scalar
     * @param outputs a byte matrix of N byte arrays {@link GroupElementsLibraryAdapter#groupElementsSize(int)}size to hold the internal representation of the generator point times the scalar in {@code scalars}
     * @return {@link GroupElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    public native int groupElementsBatchScalarMul(final int group, final byte[][] scalars, final byte[][] outputs);

    /**
     * Returns the point that is the result of the total sum a collection of points
     *
     * @param group on which of the groups of the curve to perform the operation
     * @param input a byte matrix representing a list of N byte arrays of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} representing each point
     * @param output a {@link GroupElementsLibraryAdapter#groupElementsSize(int)} array to hold the internal representation of the point
     * @return {@link GroupElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    public native int groupElementsBatchAdd(final int group, final byte[][] input, final byte[] output);

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
    public native int pairingsEquals(
            final byte[] value1, final byte[] value2, final byte[] value3, final byte[] value4);
}
