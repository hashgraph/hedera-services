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

package com.hedera.node.app.tss.cryptography.altbn128.facade;

import com.hedera.node.app.tss.cryptography.altbn128.AltBn128Exception;
import com.hedera.node.app.tss.cryptography.altbn128.adapter.FieldElementsLibraryAdapter;
import com.hedera.node.app.tss.cryptography.altbn128.adapter.GroupElementsLibraryAdapter;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.Objects;

import static com.hedera.node.app.tss.cryptography.utils.ValidationUtils.validateSize;

/**
 * This class acts as a facade that simplifies the interaction for operating specifically with any of the alt-bn-128 {@code Group}s
 *  and its {@code GroupElement}s {@code byte[]} representations.
 *  It abstracts the complexities of dealing with return codes and input and output parameters
 *  providing a higher-level interface easier to interact with from Java.
 **/
public final class GroupFacade {

    /** The underlying library adapter */
    private final GroupElementsLibraryAdapter adapter;

    private final int group;
    /** The occupied size in bytes of the GroupElement representations */
    private final int size;
    /** The occupied size in bytes of the random seed */
    private final int randomSeedSize;
    /** The occupied size in bytes of the scalar */
    private final int fieldElementsSize;

    /**
     * Creates an instance of this facade.
     * @param group in which group perform the operations.
     * @param adapter the adapter containing the underlying logic.
     * @param fieldElementsSize size in bytes of the scalar field.
     */
    public GroupFacade(
            final int group, @NonNull final GroupElementsLibraryAdapter adapter, final int fieldElementsSize) {
        this.group = group;
        this.adapter = Objects.requireNonNull(adapter, "adapter must not be null");
        this.size = adapter.groupElementsSize(group);
        this.randomSeedSize = adapter.randomSeedSize();
        this.fieldElementsSize = fieldElementsSize;
    }

    /**
     * Creates a Group2 point from a random seed.
     * @param seed the byte array seed.
     * @return the byte array representation of the Group2 point.
     * @throws AltBn128Exception in case of error.
     */
    public byte[] fromSeed(@NonNull final byte[] seed) {
        validateSize(seed, randomSeedSize, "Invalid random seed size");
        final byte[] output = new byte[size];
        final int result = adapter.groupElementsFromSeed(group, seed, output);
        if (result != GroupElementsLibraryAdapter.SUCCESS) {
            throw new AltBn128Exception(result, "groupElementsFromSeed in" + this.group);
        }
        return output;
    }

    /**
     * Attempts to create a point from an x coordinate
     * @param xCoordinate the x coordinate array
     * @return the byte array representation of the point or null if the point is not in the curve.
     * @throws AltBn128Exception in case of error
     */
    public @Nullable byte[] fromXCoordinate(@NonNull final byte[] xCoordinate) {
        final byte[] output = new byte[size];
        final int result = adapter.groupElementsFromXCoordinate(group, xCoordinate, output);
        return switch (result) {
            case GroupElementsLibraryAdapter.SUCCESS -> output;
            case GroupElementsLibraryAdapter.NOT_IN_CURVE -> null;
            default -> throw new AltBn128Exception(result, "fromXCoordinate in" + this.group);
        };
    }

    /**
     * Returns the Group2 point at infinity.
     * @return the byte array representation of the point at infinity.
     * @throws AltBn128Exception in case of error.
     */
    public byte[] zero() {
        final byte[] output = new byte[size];
        final int result = adapter.groupElementsZero(group, output);
        if (result != GroupElementsLibraryAdapter.SUCCESS) {
            throw new AltBn128Exception(result, "groupElementsZero in" + this.group);
        }
        return output;
    }

    /**
     * Returns the Group2 generator point.
     * @return the byte array representation of the generator point.
     * @throws AltBn128Exception in case of error.
     */
    public byte[] generator() {
        final byte[] output = new byte[size];
        final int result = adapter.groupElementsGenerator(group, output);
        if (result != GroupElementsLibraryAdapter.SUCCESS) {
            throw new AltBn128Exception(result, "groupElementsGenerator in" + this.group);
        }
        return output;
    }

    /**
     * Checks if two Group2 points are equal.
     * @param point1 the first point.
     * @param point2 the second point.
     * @return true if points are equal, false otherwise.
     * @throws AltBn128Exception in case of error.
     */
    public boolean equals(@NonNull final byte[] point1, @NonNull final byte[] point2) {
        if (point1.length != point2.length) {
            return false;
        }
        final int result = adapter.groupElementsEquals(group, point1, point2);
        if (result < GroupElementsLibraryAdapter.SUCCESS) {
            throw new AltBn128Exception(result, "groupElementsEquals in" + this.group);
        }
        return result == 1;
    }

    /**
     * Adds two Group2 points together.
     * @param point1 the first point.
     * @param point2 the second point.
     * @return the byte array representation of the resulting point.
     * @throws AltBn128Exception in case of error.
     */
    public byte[] add(@NonNull final byte[] point1, @NonNull final byte[] point2) {
        validateSize(point1, size, "Invalid point size");
        validateSize(point2, size, "Invalid point size");
        final byte[] output = new byte[size];
        final int result = adapter.groupElementsAdd(group, point1, point2, output);
        if (result != GroupElementsLibraryAdapter.SUCCESS) {
            throw new AltBn128Exception(result, "groupElementsAdd in" + this.group);
        }
        return output;
    }

    /**
     * Performs scalar multiplication between a Group2 point and a scalar.
     * @param point the Group2 point representation.
     * @param scalar the scalar.
     * @return the byte array representation of the resulting point.
     * @throws AltBn128Exception in case of error.
     */
    public byte[] scalarMul(@NonNull final byte[] point, @NonNull final byte[] scalar) {
        validateSize(point, size, "Invalid point size");
        validateSize(scalar, fieldElementsSize, "Invalid scalar size");
        final byte[] output = new byte[size];
        final int result = adapter.groupElementsScalarMul(group, point, scalar, output);
        if (result != GroupElementsLibraryAdapter.SUCCESS) {
            throw new AltBn128Exception(result, "groupElementsScalarMul in" + this.group);
        }
        return output;
    }

    /**
     * Converts an affine serialized point back into its internal representation.
     * This method takes a byte array representing the affine serialization of a point
     * and converts it back to its internal representation.
     *
     * @param bytes a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} to validate if is a right point
     * @return if valid the same {@code bytes} array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} containing the internal representation of the point
     * @throws NullPointerException if the bytes is null
     * @throws IllegalArgumentException if the bytes is of invalid size or the point does not belong to the curve
     * @throws AltBn128Exception in case of error.
     */
    public byte[] fromBytes(@NonNull final byte[] bytes) {
        validateSize(Objects.requireNonNull(bytes, "bytes must not be null"), this.size, "Invalid representation size");

        int result = adapter.groupElementsBytes(group, bytes);
        if (result == GroupElementsLibraryAdapter.NOT_IN_CURVE) {
            throw new IllegalArgumentException("The point is not in curve");
        } else if (result != GroupElementsLibraryAdapter.SUCCESS) {
            throw new AltBn128Exception(result, "groupElementsBytes in" + this.group);
        }

        return bytes;
    }

    /**
     * Sums a collection of points and returns the resulting point.
     * This method takes a list of points (each in internal representation) and returns
     * the point that is the result of summing all the points together.
     *
     * @param points a byte matrix representing a list of N byte arrays of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} representing each point
     * @return a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} containing representation of the resulting point.
     * @throws NullPointerException if points is null
     * @throws AltBn128Exception in case of an error during the batch addition
     */
    public byte[] batchAdd(@NonNull final byte[][] points) {
        Objects.requireNonNull(points, "points must not be null");
        final byte[] output = new byte[size];
        int result = adapter.groupElementsBatchAdd(group, points, output);
        if (result != GroupElementsLibraryAdapter.SUCCESS) {
            throw new AltBn128Exception(result, "groupElementsBatchAdd in" + this.group);
        }
        return output;
    }

    /**
     * Return the occupied size in bytes of this group2Elements representations.
     * @return the occupied size in bytes of this group2Elements representations.
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
     * multiplies each scalar in a collection for the generator point and returns the resulting points.
     * This method takes a list of scalars (each in internal representation) and returns
     * the list of point that is the result of multiplying the scalar for the generator point.
     *
     * @param scalars a byte matrix representing a list of N scalars of {@link FieldElementsLibraryAdapter#fieldElementsSize()}  representing each scalar
     * @return N points each as a byte array of {@link GroupElementsLibraryAdapter#groupElementsSize(int)} containing representation of the resulting point.
     * @throws NullPointerException if scalars is null
     * @throws AltBn128Exception in case of an error during the batch addition
     */
    public byte[][] batchMultiply(final byte[][] scalars) {
        Objects.requireNonNull(scalars, "scalars must not be null");
        final byte[][] array = new byte[scalars.length][this.size];
        int result = adapter.groupElementsBatchScalarMul(group, scalars, array);
        if (result != GroupElementsLibraryAdapter.SUCCESS) {
            throw new AltBn128Exception(result, "groupElementsBatchScalarMul in " + this.group);
        }
        return array;
    }
}
