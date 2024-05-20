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

import com.swirlds.platform.tss.pairings.Curve;

/**
 * BLS12-381 curve.
 */
public class Bls12381Curve
        implements Curve<Bls12381Curve, Bls12381FieldElement, Bls12381Group1Element, Bls12381Group2Element> {
    /**
     * The byte identifier for this curve.
     */
    public static final byte ID_BYTE = 1;

    /**
     * {@inheritDoc}
     */
    @Override
    public byte idByte() {
        return ID_BYTE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bls12381Field getField() {
        return Bls12381Field.getInstance();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bls12381Group1 getGroup1() {
        return Bls12381Group1.getInstance();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bls12381Group2 getGroup2() {
        return Bls12381Group2.getInstance();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bls12381BilinearMap getBilinearMap() {
        return Bls12381BilinearMap.getInstance();
    }
}
