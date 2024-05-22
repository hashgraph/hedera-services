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

package com.swirlds.platform.tss.bls.impl.internal;

import com.swirlds.platform.tss.bls.api.BilinearPairing;
import com.swirlds.platform.tss.bls.api.CurveType;
import com.swirlds.platform.tss.bls.impl.bls12381.Bls12381BilinearPairing;

// REVIEW this class is coupled to CurveType class, and only by having both of them declare the same constants
// is not a good approach, but it was needed in order to be able to separate the api from the internal implementing
// details.
// this class enables only exposing package .api and hide all others.
// maybe there is a better alternative that is worth analyzing using SPI.
public enum CurveTypeMapping {
    BLS_12_381(Bls12381BilinearPairing.getInstance()),
    ALT_BN_128(null);

    final BilinearPairing pairing;

    CurveTypeMapping(BilinearPairing pairing) {
        this.pairing = pairing;
    }

    public static BilinearPairing getPairing(CurveType curveType) {
        return CurveTypeMapping.valueOf(curveType.name()).pairing;
    }
}
