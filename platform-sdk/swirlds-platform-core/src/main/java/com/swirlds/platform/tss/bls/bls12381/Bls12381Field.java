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

package com.swirlds.platform.tss.bls.bls12381;

import com.swirlds.platform.tss.pairings.Field;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents the finite field used in BLS12-381
 */
public class Bls12381Field
        implements Field<Bls12381Curve, Bls12381FieldElement, Bls12381Group1Element, Bls12381Group2Element> {
    private static final Bls12381Field INSTANCE = new Bls12381Field();

    /**
     * Hidden constructor
     */
    private Bls12381Field() {}

    /**
     * @return the singleton instance of this class
     */
    @NonNull
    public static Bls12381Field getInstance() {
        return INSTANCE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381FieldElement elementFromLong(final long inputLong) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381FieldElement zeroElement() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381FieldElement oneElement() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381FieldElement randomElement(final byte[] seed) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381FieldElement deserializeElementFromBytes(final byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getElementSize() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSeedSize() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
