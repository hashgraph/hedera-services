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

import com.swirlds.pairings.api.GroupElement;
import com.swirlds.pairings.api.PairingResult;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A BLS12-381 implementation of a pairing result.
 *
 * @param element1 the first element. must be in the opposite group of the second element
 * @param element2 the second element. must be in the opposite group of the first element
 */
public record Bls12381PairingResult(@NonNull GroupElement element1, @NonNull GroupElement element2)
        implements PairingResult {

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public GroupElement getInputElement1() {
        return element1;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public GroupElement getInputElement2() {
        return element2;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public byte[] getPairingBytes() {
        throw new UnsupportedOperationException("Pairing result serialization is not supported");
    }

    // TODO: implement equals and hashCode
}
