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

import com.swirlds.platform.tss.pairings.GroupElement;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An element in G1 group used in BLS12-381
 */
public class Bls12381Group1Element
        implements GroupElement<Bls12381Group1Element, Bls12381FieldElement, Bls12381Group1, Bls12381Field> {
    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381Group1 group() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] toBytes() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381Group1Element power(@NonNull final Bls12381FieldElement exponent) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381Group1Element multiply(@NonNull final Bls12381Group1Element other) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381Group1Element divide(@NonNull final Bls12381Group1Element other) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381Group1Element compress() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCompressed() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bls12381Group1Element copy() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isValid() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
