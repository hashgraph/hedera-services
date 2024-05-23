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

package com.swirlds.platform.hcm.impl.pairings.bls12381;

import com.swirlds.platform.hcm.api.pairings.CurveType;
import com.swirlds.platform.hcm.api.pairings.Field;
import com.swirlds.platform.hcm.api.pairings.FieldElement;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents the finite field used in BLS12-381
 */
public class Bls12381Field implements Field {
    private static final Bls12381Field INSTANCE = new Bls12381Field();

    /**
     * Hidden constructor
     */
    Bls12381Field() {}

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
        return null;
    }

    @NonNull
    @Override
    public FieldElement zeroElement() {
        return null;
    }

    @NonNull
    @Override
    public FieldElement oneElement() {
        return null;
    }

    @NonNull
    @Override
    public FieldElement randomElement(final byte[] seed) {
        return null;
    }

    @NonNull
    @Override
    public FieldElement randomElement() {
        return null;
    }

    @NonNull
    @Override
    public FieldElement elementFromBytes(final byte[] bytes) {
        checkSameCurveType(bytes);
        return null;
    }

    @Override
    public int getElementSize() {
        return 0;
    }

    @Override
    public int getSeedSize() {
        return 0;
    }

    @Override
    public CurveType curveType() {
        return CurveType.BLS12_381;
    }
}
