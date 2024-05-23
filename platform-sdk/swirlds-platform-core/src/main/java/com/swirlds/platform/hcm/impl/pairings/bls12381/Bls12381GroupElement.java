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
import com.swirlds.platform.hcm.api.pairings.FieldElement;
import com.swirlds.platform.hcm.api.pairings.GroupElement;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An element in G1 group used in BLS12-381
 */
public class Bls12381GroupElement implements GroupElement {

    @NonNull
    @Override
    public GroupElement power(@NonNull final FieldElement exponent) {
        return null;
    }

    @NonNull
    @Override
    public GroupElement multiply(@NonNull final GroupElement other) {
        return null;
    }

    @NonNull
    @Override
    public GroupElement add(@NonNull final GroupElement other) {
        return null;
    }

    @NonNull
    @Override
    public GroupElement divide(@NonNull final GroupElement other) {
        return null;
    }

    @NonNull
    @Override
    public GroupElement compress() {
        return null;
    }

    @Override
    public boolean isCompressed() {
        return false;
    }

    @NonNull
    @Override
    public GroupElement copy() {
        return null;
    }

    @Override
    @NonNull
    public byte[] toBytes() {
        return new byte[0];
    }

    @Override
    @NonNull
    public CurveType curveType() {
        return CurveType.BLS12_381;
    }
}
