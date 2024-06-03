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

package com.swirlds.pairings.bls12381.impl;

import static com.swirlds.pairings.bls12381.impl.Utils.computeSha256;
import static com.swirlds.pairings.bls12381.impl.jni.Bls12381Bindings.*;

import com.swirlds.pairings.api.BilinearPairing;
import com.swirlds.pairings.api.Group;
import com.swirlds.pairings.api.GroupElement;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;

/**
 * G1 group used in BLS12-381
 */
public class Bls12381Group2 implements Group {
    private static final Bls12381Group2 INSTANCE = new Bls12381Group2();

    /** Length of a byte array representing a compressed element */
    private static final int COMPRESSED_SIZE = 96;

    /** Length of a byte array representing an uncompressed element */
    private static final int UNCOMPRESSED_SIZE = 192;

    /** Required size of a seed to create a new group element */
    private static final int SEED_SIZE = 32;

    /** Hidden constructor */
    private Bls12381Group2() {}

    /**
     * Returns the singleton
     *
     * @return the singleton
     */
    public static Bls12381Group2 getInstance() {
        return INSTANCE;
    }

    @NonNull
    @Override
    public BilinearPairing getPairing() {
        return Bls12381BilinearPairing.getInstance();
    }

    @NonNull
    @Override
    public GroupElement getGenerator() {
        return null;
    }

    @NonNull
    @Override
    public GroupElement zeroElement() {
        final byte[] output = new byte[UNCOMPRESSED_SIZE];

        final int errorCode = newG2Identity(output);
        if (errorCode != SUCCESS) {
            throw new Bls12381Exception("newG2Identity", errorCode);
        }

        return new Bls12381Group2Element(output);
    }

    @NonNull
    @Override
    public GroupElement randomElement(@NonNull final byte[] seed) {
        if (seed.length != SEED_SIZE) {
            throw new IllegalArgumentException(String.format("seed must be %d bytes in length", SEED_SIZE));
        }

        final byte[] output = new byte[UNCOMPRESSED_SIZE];

        final int errorCode = newRandomG2(seed, output);
        if (errorCode != SUCCESS) {
            throw new Bls12381Exception("newRandomG2", errorCode);
        }

        return new Bls12381Group2Element(output);
    }

    @NonNull
    @Override
    public GroupElement elementFromHash(@NonNull final byte[] input) {
        return randomElement(computeSha256(input));
    }

    @NonNull
    @Override
    public GroupElement batchAdd(@NonNull final Collection<GroupElement> elements) {
        if (elements.isEmpty()) {
            throw new IllegalArgumentException("Empty collection is invalid");
        }

        if (elements.contains(null)) {
            throw new IllegalArgumentException("invalid element (null) in collection");
        }

        final Bls12381Group2Element[] elementArray = new Bls12381Group2Element[elements.size()];

        int count = 0;
        for (final GroupElement element : elements) {
            elementArray[count] = (Bls12381Group2Element) element;
            ++count;
        }

        final byte[] output = new byte[UNCOMPRESSED_SIZE];

        final int errorCode = g2BatchMultiply(elementArray, output);
        if (errorCode != SUCCESS) {
            throw new Bls12381Exception("g2BatchMultiply", errorCode);
        }

        return new Bls12381Group2Element(output);
    }

    @NonNull
    @Override
    public GroupElement elementFromBytes(@NonNull final byte[] inputBytes) {
        return new Bls12381Group2Element(inputBytes);
    }

    @Override
    public int getCompressedSize() {
        return COMPRESSED_SIZE;
    }

    @Override
    public int getUncompressedSize() {
        return UNCOMPRESSED_SIZE;
    }

    @Override
    public int getSeedSize() {
        return SEED_SIZE;
    }

    // TODO: implement equals and hashCode
}
