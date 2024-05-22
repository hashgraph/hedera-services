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
import com.swirlds.platform.hcm.api.pairings.GroupElement;
import com.swirlds.platform.hcm.api.pairings.PairingResult;
import edu.umd.cs.findbugs.annotations.NonNull;

public class Bls12381PairingResult implements PairingResult {
    public Bls12381PairingResult(GroupElement aggregatedSignature, GroupElement g1) {
        aggregatedSignature.checkSameCurveType(g1);
        aggregatedSignature.checkSameCurveType(this);
    }

    @Override
    public PairingResult fromBytes(byte[] bytes) {
        return null;
    }

    @Override
    public boolean isEquals(@NonNull PairingResult other) {
        return false;
    }

    @Override
    public byte[] toBytes() {
        return new byte[0];
    }

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public CurveType curveType() {
        return CurveType.BLS12_381;
    }
}
