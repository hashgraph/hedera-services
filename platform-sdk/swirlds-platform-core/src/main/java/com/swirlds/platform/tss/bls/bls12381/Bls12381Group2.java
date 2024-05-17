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

import com.swirlds.platform.tss.pairings.Group;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;

/**
 * Represents the G2 group used in BLS12-381
 */
public class Bls12381Group2
        implements Group<Bls12381Group2Element, Bls12381FieldElement, Bls12381Group2, Bls12381Field> {
    private static final Bls12381Group2 INSTANCE = new Bls12381Group2();

    /**
     * Hidden constructor
     */
    private Bls12381Group2() {}

    /**
     * @return the singleton instance of this class
     */
    @NonNull
    public static Bls12381Group2 getInstance() {
        return INSTANCE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381Group2Element getGenerator() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381Group2Element oneElement() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381Group2Element randomElement(final byte[] seed) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381Group2Element hashToGroup(final byte[] input) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381Group2Element batchMultiply(@NonNull final Collection<Bls12381Group2Element> elements) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381Group2Element deserializeElementFromBytes(final byte[] bytes) {
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
