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
import com.swirlds.pairings.api.Field;
import com.swirlds.pairings.api.FieldElement;
import com.swirlds.pairings.bls12381.impl.jni.Bls12381Bindings;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents the finite field used in BLS12-381
 */
public class Bls12381Field implements Field {
    private static final Bls12381Field INSTANCE = new Bls12381Field();

    /** Required size of a seed to create a new field element */
    public static final int SEED_SIZE = 32;

    /** Length of a byte array representing a field element */
    public static final int ELEMENT_BYTE_SIZE = 32;

    /** The singleton instance */
    private static Bls12381Field instance;

    /** Hidden constructor */
    private Bls12381Field() {}

    /**
     * @return the singleton instance of this class
     */
    @NonNull
    public static Bls12381Field getInstance() {
        return INSTANCE;
    }

    @NonNull
    @Override
    public FieldElement elementFromLong(final long inputLong) {
        final byte[] output = new byte[ELEMENT_BYTE_SIZE];

        final int errorCode = Bls12381Bindings.newScalarFromLong(inputLong, output);
        if (errorCode != Bls12381Bindings.SUCCESS) {
            throw new Bls12381Exception("newScalarFromLong", errorCode);
        }

        return new Bls12381FieldElement(output);
    }

    @NonNull
    @Override
    public FieldElement randomElement(@NonNull final byte[] seed) {
        if (seed.length != SEED_SIZE) {
            throw new IllegalArgumentException(String.format("seed must be %s bytes in length", SEED_SIZE));
        }

        final byte[] output = new byte[ELEMENT_BYTE_SIZE];

        final int errorCode = Bls12381Bindings.newRandomScalar(seed, output);
        if (errorCode != Bls12381Bindings.SUCCESS) {
            throw new Bls12381Exception("newRandomScalar", errorCode);
        }

        return new Bls12381FieldElement(output);
    }

    @NonNull
    @Override
    public FieldElement elementFromBytes(@NonNull final byte[] bytes) {
        return new Bls12381FieldElement(bytes);
    }

    @Override
    public int getElementSize() {
        return ELEMENT_BYTE_SIZE;
    }

    @Override
    public int getSeedSize() {
        return SEED_SIZE;
    }

    @NonNull
    @Override
    public BilinearPairing getPairing() {
        return Bls12381BilinearPairing.getInstance();
    }

    // TODO: implement equals and hashCode
}
