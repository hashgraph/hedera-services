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

package com.swirlds.platform.hcm.impl.internal;

import com.swirlds.platform.hcm.api.pairings.BilinearPairing;
import com.swirlds.platform.hcm.api.pairings.CurveType;
import com.swirlds.platform.hcm.impl.pairings.bls12381.Bls12381BilinearPairing;

// REVIEW this class is coupled to CurveType class, and only by having both of them declare the same constants
// is not a good approach, but it was needed in order to be able to separate the api from the internal implementing
// details.
// this class enables only exposing package .api and hide all others.
// maybe there is a better alternative that is worth analyzing using SPI.
public enum CurveTypeMapping {
    BLS_12_381(Bls12381BilinearPairing.getInstance(), GroupAssignment.GROUP1_FOR_SIGNING),
    ALT_BN_128(null, null);

    final BilinearPairing pairing;
    final GroupAssignment assignment;

    CurveTypeMapping(final BilinearPairing pairing, final GroupAssignment assignment) {
        this.pairing = pairing;
        this.assignment = assignment;
    }

    public static CurveTypeMapping getPairing(CurveType curveType) {
        return CurveTypeMapping.valueOf(curveType.name());
    }

    public BilinearPairing getPairing() {
        return pairing;
    }
}
