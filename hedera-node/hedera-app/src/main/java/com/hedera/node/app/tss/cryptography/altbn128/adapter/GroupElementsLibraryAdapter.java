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
 * This interface defines a contract and any third party library that provides the functionality for handling Elliptic Curve Group and Points must adhere to.
 *
 *  @apiNote This contract is not Java friendly, and it is defined in a way that is easy to implement in other languages.
 *  All operations return a status code, where 0 mean success, and a non-zero result means a codified error callers must know how to deal with.
 *  As the  code does not guarantee validation of parameters, Input and output parameters must be provided and instantiated accordingly for the invocation to be performed safety.
 *  i.e.:Sending non-null values and correctly instantiated arrays (expected size) is responsibility of the caller.
 * @implSpec Implementations are not forced to perform validations on the expected size of the arrays or the nullity of the parameters, so that remains a callers responsibility.
 */
public interface GroupElementsLibraryAdapter extends RandomElementsAdapter {

    /** The return code that represents that a call succeeded */
    int SUCCESS = 0;

    /** The return code that represents that the requested point is not in the curve */
    int NOT_IN_CURVE = -4;

    /**
     * Creates a GroupElement byte internal representation from a seed byte array
     * @param group on which of the groups of the curve to perform the operation
     * @param input a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} that contains the internal represents the point
     * @param output a {@link GroupElementsLibraryAdapter#groupElementsSize(int)} array to hold the internal representation of the point
     * @return {@link GroupElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    int groupElementsFromSeed(final int group, final byte[] input, final byte[] output);

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
    int groupElementsFromXCoordinate(final int group, final byte[] input, final byte[] output);

    /**
     * Returns the GroupElement byte internal representation of the point at infinity
     * @param group on which of the groups of the curve to perform the operation
     * @param output a {@link GroupElementsLibraryAdapter#groupElementsSize(int)} array to hold the internal representation of the point
     * @return {@link GroupElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    int groupElementsZero(final int group, final byte[] output);

    /**
     * Returns the GroupElement byte internal representation of the generator point of the group
     * @param group on which of the groups of the curve to perform the operation
     * @param output a {@link GroupElementsLibraryAdapter#groupElementsSize(int)} array to hold the internal representation of the point
     * @return {@link GroupElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    int groupElementsGenerator(final int group, final byte[] output);

    /**
     * returns if two representations are the same
     *
     * @param group on which of the groups of the curve to perform the operation
     * @param value a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} that contains the internal represents the point
     * @param other a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)}  that contains the internal represents the point
     * @return 0 if false, 1 if true, or a less than zero error code if there was an error
     */
    int groupElementsEquals(final int group, final byte[] value, final byte[] other);

    /**
     * Returns the byte size of a groupElement internal representation.
     *
     * @param group on which of the groups of the curve to perform the operation
     * @return the byte size of a groupElement internal representation.
     */
    int groupElementsSize(final int group);

    /**
     * Returns the addition of code {@code value} and  {@code other}.
     *
     * @param group on which of the groups of the curve to perform the operation
     * @param value a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} that contains the internal represents the point
     * @param other a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} that contains the internal represents the point
     * @param output a {@link GroupElementsLibraryAdapter#groupElementsSize(int)} array to hold the internal representation of the point
     * @return 0 if false, 1 if true, or a less than zero error code if there was an error
     */
    int groupElementsAdd(final int group, final byte[] value, final byte[] other, final byte[] output);

    /**
     * Returns the scalar multiplication between code {@code point} and {@code scalar}.
     *
     * @param group on which of the groups of the curve to perform the operation
     * @param point a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} that will be used as the seed to create the point
     * @param scalar a byte array of {@link FieldElementsLibraryAdapter#fieldElementsSize()}} that contains the representation of the scalar
     * @param output a {@link GroupElementsLibraryAdapter#groupElementsSize(int)} array to hold the internal representation of the point
     * @return {@link GroupElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    int groupElementsScalarMul(final int group, final byte[] point, final byte[] scalar, final byte[] output);

    /**
     * Validates if a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} is a valid representation of a point in the curve
     *
     * @param group on which of the groups of the curve to perform the operation
     * @param point a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} that will be used as the seed to create the
     *              point
     * @return {@link GroupElementsLibraryAdapter#SUCCESS} for success, {@link GroupElementsLibraryAdapter#NOT_IN_CURVE} if the point is invalid or a less than zero error code if there was an error
     */
    int groupElementsBytes(final int group, final byte[] point);

    /**
     * Returns the result of the multiplication of the {@link GroupElementsLibraryAdapter#groupElementsGenerator(int, byte[])} for each scalar in the {@code scalars} list
     *
     * @param group on which of the groups of the curve to perform the operation
     * @param scalars a byte matrix representing a list of N byte arrays of {@link FieldElementsLibraryAdapter#fieldElementsSize()}} size each representing a scalar
     * @param outputs a byte matrix of N byte arrays {@link GroupElementsLibraryAdapter#groupElementsSize(int)}size to hold the internal representation of the generator point times the scalar in {@code scalars}
     * @return {@link GroupElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    int groupElementsBatchScalarMul(final int group, final byte[][] scalars, final byte[][] outputs);

    /**
     * Returns the point that is the result of the total sum a collection of points
     *
     * @param group on which of the groups of the curve to perform the operation
     * @param input a byte matrix representing a list of N byte arrays of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} representing each point
     * @param output a {@link GroupElementsLibraryAdapter#groupElementsSize(int)} array to hold the internal representation of the point
     * @return {@link GroupElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    int groupElementsBatchAdd(final int group, final byte[][] input, final byte[] output);
}
