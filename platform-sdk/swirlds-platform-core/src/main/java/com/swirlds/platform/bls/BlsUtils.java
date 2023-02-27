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
package com.swirlds.platform.bls;

import com.hedera.platform.bls.api.BilinearMap;
import com.hedera.platform.bls.api.GroupElement;

/** Class containing utility helper functions */
public class BlsUtils {

    /** Hidden constructor */
    private BlsUtils() {}

    /**
     * Asserts that a given group element is a member of the signature group (G1)
     *
     * @param bilinearMap the bilinear map the group is part of
     * @param groupElement the group element to assert membership of
     * @param elementName the name of the element, to record errors
     */
    public static void assertSignatureGroupMembership(
            final BilinearMap bilinearMap,
            final GroupElement groupElement,
            final String elementName) {

        if (!(groupElement.group().getClass().equals(bilinearMap.signatureGroup().getClass()))) {
            throw new IllegalArgumentException(
                    String.format(
                            "%s must be in the signature group of the bilinear map", elementName));
        }
    }

    /**
     * Asserts that a given group element is a member of the public key group (G2)
     *
     * @param bilinearMap the bilinear map the group is part of
     * @param groupElement the group element to assert membership of
     * @param elementName the name of the element, to record errors
     */
    public static void assertPublicKeyGroupMembership(
            final BilinearMap bilinearMap,
            final GroupElement groupElement,
            final String elementName) {

        if (!(groupElement.group().getClass().equals(bilinearMap.keyGroup().getClass()))) {
            throw new IllegalArgumentException(
                    String.format(
                            "%s must be in the public key group of the bilinear map", elementName));
        }
    }
}
