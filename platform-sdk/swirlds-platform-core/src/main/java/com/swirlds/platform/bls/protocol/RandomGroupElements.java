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

import static com.swirlds.common.test.RandomUtils.randomByteArray;
import static com.swirlds.platform.bls.BlsUtils.assertPublicKeyGroupMembership;

import com.hedera.platform.bls.api.BilinearMap;
import com.hedera.platform.bls.api.GroupElement;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;

/**
 * Holds random group elements needed by {@link CrsProtocol}
 *
 * <p>The random group elements in this object are in the key group of the bilinear map, since the
 * random group elements from all parties will be aggregated to create the {@link Crs} object generators, that must be
 * in the key group
 */
public class RandomGroupElements {

    /** The first random group element, in the public key group of the bilinear map */
    private final GroupElement randomGroupElement1;

    /** The second random group element, in the public key group of the bilinear map */
    private final GroupElement randomGroupElement2;

    /**
     * The underlying bilinear map. The random elements are part of the key group of this bilinear map
     */
    private final BilinearMap bilinearMap;

    /**
     * Constructor that generates new random group elements
     *
     * @param bilinearMap the underlying bilinear map
     * @param random      a source of randomness
     */
    public RandomGroupElements(final BilinearMap bilinearMap, final Random random) {
        this.randomGroupElement1 = bilinearMap
                .keyGroup()
                .randomElement(randomByteArray(random, 32))
                .compress();

        this.randomGroupElement2 = bilinearMap
                .keyGroup()
                .randomElement(randomByteArray(random, 32))
                .compress();

        this.bilinearMap = bilinearMap;
    }

    /**
     * Constructor for when random group elements have already been generated. Both random elements must be in the
     * public key group of the bilinear map.
     *
     * @param bilinearMap         the underlying bilinear map
     * @param randomGroupElement1 the first random group element
     * @param randomGroupElement2 the second random group element
     */
    public RandomGroupElements(
            final BilinearMap bilinearMap,
            final GroupElement randomGroupElement1,
            final GroupElement randomGroupElement2) {

        assertPublicKeyGroupMembership(bilinearMap, randomGroupElement1, "randomGroupElement1");
        assertPublicKeyGroupMembership(bilinearMap, randomGroupElement2, "randomGroupElement2");

        this.randomGroupElement1 = randomGroupElement1;
        this.randomGroupElement2 = randomGroupElement2;

        this.bilinearMap = bilinearMap;
    }

    /**
     * Gets the first random group element
     *
     * @return {@link #randomGroupElement1}
     */
    public GroupElement getRandomGroupElement1() {
        return randomGroupElement1;
    }

    /**
     * Gets the second random group element
     *
     * @return {@link #randomGroupElement2}
     */
    public GroupElement getRandomGroupElement2() {
        return randomGroupElement2;
    }

    /**
     * Gets the underlying bilinear map
     *
     * @return the bilinear map
     */
    public BilinearMap getBilinearMap() {
        return bilinearMap;
    }

    /**
     * Generates a commitment to the random group elements
     *
     * @param digest the digest to use to generate the commitment
     * @return the generated commitment
     */
    public byte[] commit(final MessageDigest digest) {

        digest.update(randomGroupElement1.toBytes());
        digest.update(randomGroupElement2.toBytes());

        return digest.digest();
    }

    @Override
    public String toString() {
        return "randomGroupElement1: "
                + Arrays.toString(randomGroupElement1.toBytes())
                + "\n"
                + "randomGroupElement2: "
                + Arrays.toString(randomGroupElement2.toBytes());
    }
}
