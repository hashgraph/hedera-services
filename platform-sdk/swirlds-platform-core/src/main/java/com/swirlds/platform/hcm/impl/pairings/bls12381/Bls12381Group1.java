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
import com.swirlds.platform.hcm.api.pairings.Group;
import com.swirlds.platform.hcm.api.pairings.GroupElement;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * G1 group used in BLS12-381
 */
public class Bls12381Group1 implements Group {
    private static final Bls12381Group1 INSTANCE = new Bls12381Group1();

    /**
     * Hidden constructor
     */
    private Bls12381Group1() {}

    /**
     * @return the singleton instance of this class
     */
    @NonNull
    public static Bls12381Group1 getInstance() {
        return INSTANCE;
    }

    @NonNull
    @Override
    public BilinearPairing getPairing() {
        return Bls12381BilinearPairing.getInstance();
    }

    @NonNull
    @Override
    public GroupElement getGenerator() {
        return null;
    }

    @NonNull
    @Override
    public GroupElement oneElement() {
        return null;
    }

    @NonNull
    @Override
    public GroupElement randomElement(final byte[] seed) {
        return null;
    }

    @NonNull
    @Override
    public GroupElement randomElement() {
        return null;
    }

    @NonNull
    @Override
    public GroupElement elementFromHash(final byte[] input) {
        return null;
    }

    @NonNull
    @Override
    public GroupElement batchMultiply(@NonNull final GroupElement elements) {
        return null;
    }

    @NonNull
    @Override
    public GroupElement elementFromBytes(final byte[] bytes) {
        return null;
    }

    @Override
    public int getCompressedSize() {
        return 0;
    }

    @Override
    public int getUncompressedSize() {
        return 0;
    }

    @Override
    public int getSeedSize() {
        return 0;
    }
}
