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

import com.swirlds.pairings.api.BilinearPairing;
import com.swirlds.pairings.api.Group;
import com.swirlds.pairings.api.GroupElement;
import com.swirlds.pairings.bls12381.impl.jni.Bls12381Bindings;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;

/**
 * G1 group used in BLS12-381
 */
public class Bls12381Group1 implements Group {
    private static final Bls12381Group1 INSTANCE = new Bls12381Group1();
    private static final int UNCOMPRESSED_SIZE = 0;

    /**
     * Hidden constructor
     */
    private Bls12381Group1() {}

    /**
     * @return the singleton instance of this class
     */
    @NonNull
    public static Bls12381Group1 getInstance() {
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

        final int errorCode = Bls12381Bindings.newG1Identity(output);
        if (errorCode != Bls12381Bindings.SUCCESS) {
            throw new Bls12381Exception("newG1Identity", errorCode);
        }

        return new Bls12381Group1Element(output);
    }

    @NonNull
    @Override
    public GroupElement randomElement(@NonNull final byte[] seed) {
        if (seed.length != Bls12381Field.SEED_SIZE) {
            throw new IllegalArgumentException(
                    String.format("seed must be %d bytes in length", Bls12381Field.SEED_SIZE));
        }
        final byte[] output = new byte[UNCOMPRESSED_SIZE];

        final int errorCode = Bls12381Bindings.newRandomG1(seed, output);
        if (errorCode != Bls12381Bindings.SUCCESS) {
            throw new Bls12381Exception("newRandomG1", errorCode);
        }

        return new Bls12381Group1Element(output);
    }

    @NonNull
    @Override
    public GroupElement elementFromHash(@NonNull final byte[] input) {
        return randomElement(Utils.computeSha256(input));
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

        final Bls12381Group1Element[] elementArray = new Bls12381Group1Element[elements.size()];

        int count = 0;
        for (final GroupElement element : elements) {
            elementArray[count] = (Bls12381Group1Element) element;
            ++count;
        }

        final byte[] output = new byte[UNCOMPRESSED_SIZE];

        final int errorCode = Bls12381Bindings.g1BatchMultiply(elementArray, output);
        if (errorCode != Bls12381Bindings.SUCCESS) {
            throw new Bls12381Exception("g1BatchMultiply", errorCode);
        }

        return new Bls12381Group1Element(output);
    }

    @NonNull
    @Override
    public GroupElement elementFromBytes(@NonNull final byte[] bytes) {
        return null;
    }

    @Override
    public int getCompressedSize() {
        return 0;
    }

    @Override
    public int getUncompressedSize() {
        return 0;
    }

    @Override
    public int getSeedSize() {
        return 0;
    }

    // TODO: implement equals and hashCode
}
