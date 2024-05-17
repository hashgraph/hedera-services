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

import com.swirlds.platform.tss.pairings.BilinearMap;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A BLS12-381 implementation of a bilinear map.
 */
public class Bls12381BilinearMap
        implements BilinearMap<
                Bls12381Group2Element,
                Bls12381Group1Element,
                Bls12381Group2,
                Bls12381Group1,
                Bls12381Field,
                Bls12381FieldElement> {

    private static final Bls12381BilinearMap INSTANCE = new Bls12381BilinearMap();

    /**
     * Hidden constructor
     */
    private Bls12381BilinearMap() {}

    /**
     * @return the singleton instance of this class
     */
    @NonNull
    public static Bls12381BilinearMap getInstance() {
        return INSTANCE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381FieldElement field() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381Group2 signatureGroup() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381Group1 keyGroup() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean comparePairing(
            @NonNull final Bls12381Group2Element signatureElement1,
            @NonNull final Bls12381Group1Element keyElement1,
            @NonNull final Bls12381Group2Element signatureElement2,
            @NonNull final Bls12381Group1Element keyElement2) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] displayPairing(
            @NonNull final Bls12381Group2Element signatureElement, @NonNull final Bls12381Group1Element keyElement) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
