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

import com.swirlds.platform.hcm.api.pairings.BilinearPairing;
import com.swirlds.platform.hcm.api.pairings.Field;
import com.swirlds.platform.hcm.api.pairings.Group;
import com.swirlds.platform.hcm.api.pairings.GroupElement;
import com.swirlds.platform.hcm.api.pairings.PairingResult;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A BLS12-381 implementation of a bilinear map.
 */
public class Bls12381BilinearPairing implements BilinearPairing {

    private static final Bls12381BilinearPairing INSTANCE = new Bls12381BilinearPairing();

    public static Bls12381BilinearPairing getInstance() {
        return INSTANCE;
    }

    /**
     * Hidden constructor
     */
    private Bls12381BilinearPairing() {}

    @NonNull
    @Override
    public PairingResult pairingBetween(@NonNull final GroupElement g1Element, @NonNull final GroupElement g2Element) {
        return new Bls12381PairingResult(g1Element, g2Element);
    }

    @NonNull
    @Override
    public Field getField() {
        return new Bls12381Field();
    }

    @NonNull
    @Override
    public Group getGroup1() {
        return new Bls12381Group();
    }

    @NonNull
    @Override
    public Group getGroup2() {
        return new Bls12381Group2();
    }
}
