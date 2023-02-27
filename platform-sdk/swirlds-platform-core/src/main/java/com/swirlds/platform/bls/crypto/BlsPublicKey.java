/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.swirlds.platform.bls.crypto;

import static com.swirlds.platform.bls.BlsUtils.assertPublicKeyGroupMembership;

import com.hedera.platform.bls.api.BilinearMap;
import com.hedera.platform.bls.api.GroupElement;

/**
 * A BLS public key
 *
 * @param bilinearMap the bilinear map behind the encryption scheme
 * @param keyMaterial the underlying public key material, that we do math with. in the key group of
 *     the bilinear map
 */
public record BlsPublicKey(BilinearMap bilinearMap, GroupElement keyMaterial) {

    /** Constructor, which checks the group membership of the input key material */
    public BlsPublicKey {
        assertPublicKeyGroupMembership(bilinearMap, keyMaterial, "keyMaterial");
    }

    /**
     * Copy constructor
     *
     * @param otherPublicKey the public key being copied
     */
    public BlsPublicKey(final BlsPublicKey otherPublicKey) {
        this(otherPublicKey.bilinearMap, otherPublicKey.keyMaterial.copy());
    }
}
