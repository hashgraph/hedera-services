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

import static com.swirlds.platform.bls.BlsUtils.assertPublicKeyGroupMembership;

import com.hedera.platform.bls.api.BilinearMap;
import com.hedera.platform.bls.api.GroupElement;

/**
 * The output object of {@link CrsProtocol}
 *
 * <p>Contains 2 generator points, which are used for threshold signatures and IBE encryption,
 * respectively. While it would be mathematically feasible to use a single generator to perform both
 * functions, the existing proofs were created with separate generators. This is no problem, since
 * the price of a second generator point is negligible.
 *
 * @param bilinearMap the {@link BilinearMap} signatures are created for and verified over
 * @param thresholdGenerator independent generator for BLS threshold signatures. exists in the key
 *     group of the bilinear map, since the generator is used to create share public keys
 * @param ibeGenerator independent generator, used for IBE encryption. exists in the key group of
 *     the bilinear map, since the generator is used to create IBE public keys
 */
public record Crs(BilinearMap bilinearMap, GroupElement thresholdGenerator, GroupElement ibeGenerator)
        implements BlsProtocolOutput {

    /**
     * Constructor asserting correct generator group membership
     *
     * @param bilinearMap the bilinearMap in use
     * @param thresholdGenerator the public key group generator for creating share public keys
     * @param ibeGenerator the public key group generator for creating IBE public keys
     */
    public Crs {
        assertPublicKeyGroupMembership(bilinearMap, thresholdGenerator, "thresholdGenerator");
        assertPublicKeyGroupMembership(bilinearMap, ibeGenerator, "ibeGenerator");
    }

    /** {@inheritDoc} */
    @Override
    public boolean compareToOtherOutput(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null) {
            return false;
        }

        if (o instanceof final Crs otherCrs) {
            return bilinearMap.equals(otherCrs.bilinearMap)
                    && thresholdGenerator.equals(otherCrs.thresholdGenerator)
                    && ibeGenerator.equals(otherCrs.ibeGenerator);
        }

        return false;
    }

    @Override
    public String toString() {
        return "Crs{"
                + "bilinearMap="
                + bilinearMap
                + ", thresholdGenerator="
                + thresholdGenerator
                + ", ibeGenerator="
                + ibeGenerator
                + '}';
    }
}
