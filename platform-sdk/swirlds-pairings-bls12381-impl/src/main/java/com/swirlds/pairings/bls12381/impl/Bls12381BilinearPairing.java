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

package com.swirlds.pairings.bls12381.impl;

import com.swirlds.pairings.api.*;
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
    public PairingResult pairingBetween(@NonNull final GroupElement element1, @NonNull final GroupElement element2) {
        if (!element1.isOppositeGroup(element2)) {
            throw new IllegalArgumentException("Pairing elements must be from opposite groups");
        }

        return new Bls12381PairingResult(element1, element2);
    }

    @Override
    public boolean comparePairings(
            @NonNull final GroupElement pairingAElement1,
            @NonNull final GroupElement pairingAElement2,
            @NonNull final GroupElement pairingBElement1,
            @NonNull final GroupElement pairingBElement2) {
        return false;
    }

    @NonNull
    @Override
    public Field getField() {
        return Bls12381Field.getInstance();
    }

    @NonNull
    @Override
    public Group getGroup1() {
        return Bls12381Group1.getInstance();
    }

    @NonNull
    @Override
    public Group getGroup2() {
        return Bls12381Group2.getInstance();
    }

    @NonNull
    @Override
    public Group getOtherGroup(@NonNull final Group group) {
        if (group.equals(getGroup1())) {
            return getGroup2();
        } else if (group.equals(getGroup2())) {
            return getGroup1();
        } else {
            throw new IllegalArgumentException("Unknown group");
        }
    }

    // TODO: implement equals and hashCode
}
