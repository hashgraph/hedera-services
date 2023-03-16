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

package com.swirlds.platform.bls.protocol;

import static com.swirlds.platform.bls.BlsUtils.throwIfNotPublicKeyGroup;

import com.hedera.platform.bls.api.BilinearMap;
import com.hedera.platform.bls.api.GroupElement;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The "Common Reference String" output object of {@link CrsProtocol}
 *
 * <p>Contains 2 generator points, which are used for threshold signatures and IBE encryption,
 * respectively. While it would be mathematically feasible to use a single generator to perform both functions, the
 * existing proofs were created with separate generators. This is no problem, since the price of a second generator
 * point is negligible.
 *
 * @param bilinearMap        the {@link BilinearMap} signatures are created for and verified over
 * @param thresholdGenerator independent generator for BLS threshold signatures. exists in the key group of the bilinear
 *                           map, since the generator is used to create share public keys
 * @param ibeGenerator       independent generator, used for IBE encryption. exists in the key group of the bilinear
 *                           map, since the generator is used to create IBE public keys
 */
public record Crs(
        @NonNull BilinearMap bilinearMap,
        @NonNull GroupElement thresholdGenerator,
        @NonNull GroupElement ibeGenerator) {
    /**
     * Constructor asserting correct generator group membership
     *
     * @param bilinearMap        the bilinearMap in use
     * @param thresholdGenerator the public key group generator for creating share public keys
     * @param ibeGenerator       the public key group generator for creating IBE public keys
     */
    public Crs {
        throwIfNotPublicKeyGroup(bilinearMap, thresholdGenerator, "thresholdGenerator");
        throwIfNotPublicKeyGroup(bilinearMap, ibeGenerator, "ibeGenerator");
    }
}
