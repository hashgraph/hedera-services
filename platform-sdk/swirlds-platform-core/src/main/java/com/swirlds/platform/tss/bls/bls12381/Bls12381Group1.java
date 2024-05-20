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

import com.swirlds.platform.tss.pairings.Group1;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;

/**
 * G1 group used in BLS12-381
 */
public class Bls12381Group1
        implements Group1<Bls12381Curve, Bls12381FieldElement, Bls12381Group1Element, Bls12381Group2Element> {
    private static final Bls12381Group1 INSTANCE = new Bls12381Group1();

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

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381Group1Element getGenerator() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381Group1Element oneElement() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381Group1Element randomElement(final byte[] seed) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381Group1Element hashToGroup(final byte[] input) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @NonNull
    @Override
    public Bls12381Group1Element batchMultiply(@NonNull final Collection<Bls12381Group1Element> groupElements) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381Group1Element deserializeElementFromBytes(final byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCompressedSize() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getUncompressedSize() {
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
