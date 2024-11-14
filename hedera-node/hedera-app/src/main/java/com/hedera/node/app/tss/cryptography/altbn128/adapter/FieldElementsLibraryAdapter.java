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
 * This interface defines a contract and any third party library that provides the functionality for handling Finite Fields and Scalars must adhere to.
 *
 *  @apiNote This contract is not Java friendly, and it is defined in a way that is easy to implement in other languages.
 *  All operations return a status code, where 0 mean success, and a non-zero result means a codified error callers must know how to deal with.
 *  As the native code does not guarantee validation of parameters, Input and output parameters must be provided and instantiated accordingly for the invocation to be performed safety.
 *  i.e.:Sending non-null values and correctly instantiated arrays (expected size) is responsibility of the caller.
 */
public interface FieldElementsLibraryAdapter extends RandomElementsAdapter {

    /** The return code that represents that a call succeeded */
    int SUCCESS = 0;
    /** The return code that represents that a scalar cannot be inverted */
    int CANNOT_INVERT = -5;

    /**
     * Creates a new random scalar from a seed value
     *
     * @param inputSeed the byte seed to be used to create the new scalar
     * @param output    the byte array that will be filled with the new scalar
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    int fieldElementsFromRandomSeed(final byte[] inputSeed, final byte[] output);

    /**
     * Creates a new scalar from a long
     *
     * @param inputLong the long to be used to create the new scalar
     * @param output    the byte array that will be filled with the new scalar
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    int fieldElementsFromLong(final long inputLong, final byte[] output);

    /**
     * Determines if the input representation is a valida FieldElement
     *
     * @param input  the that represents the scalar
     * @param output the byte array that will be filled with the new scalar
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    int fieldElementsFromBytes(final byte[] input, final byte[] output);

    /**
     * Creates a new zero value scalar
     *
     * @param output the byte array that will be filled with the new scalar
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    int fieldElementsZero(final byte[] output);

    /**
     * Creates a new one value scalar.
     *
     * @param output the byte array that will be filled with the new scalar
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    int fieldElementsOne(final byte[] output);

    /**
     * returns if two representations are the same
     *
     * @param value the that represents a scalar
     * @param other the that represents another scalar
     * @return 0 if false, 1 if true, or a less than zero error code if there was an error
     */
    int fieldElementsEquals(final byte[] value, final byte[] other);

    /**
     * Returns the byte size of a field element object.
     *
     * @return a non-zero error code if there was an error, otherwise 0
     */
    int fieldElementsSize();

    /**
     * Adds two scalars
     *
     * @param input1  the that represents the scalar
     * @param input2  the that represents the scalar
     * @param output the byte array that will be filled with the result of the operation
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    int fieldElementsAdd(final byte[] input1, final byte[] input2, final byte[] output);

    /**
     * subtracts two scalars
     *
     * @param input1  the that represents the scalar
     * @param input2  the that represents the scalar
     * @param output the byte array that will be filled with the result of the operation
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    int fieldElementsSubtract(final byte[] input1, final byte[] input2, final byte[] output);

    /**
     * Multiplies two scalars
     *
     * @param input1  the that represents the scalar
     * @param input2  the that represents the scalar
     * @param output the byte array that will be filled with the result of the operation
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    int fieldElementsMultiply(final byte[] input1, final byte[] input2, final byte[] output);

    /**
     * Creates powers a scalar to an exponent
     * @param input1  the that represents the scalar
     * @param exponent the long to be used to create the new scalar
     * @param output the byte array that will be filled with the result of the operation
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    int fieldElementsPow(final byte[] input1, final long exponent, final byte[] output);

    /**
     * returns the inverse scalar (value^-1)
     *
     * @param input  the that represents the scalar
     * @param output the byte array that will be filled with the result of the operation
     * @return {@link FieldElementsLibraryAdapter#SUCCESS} for success, or a less than zero error code if there was an error
     */
    int fieldElementsInverse(final byte[] input, final byte[] output);
}
