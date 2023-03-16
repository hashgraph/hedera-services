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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Class containing utility helper functions to support the BLS protocols
 */
public class BlsUtils {

    /** Hidden constructor */
    private BlsUtils() {}

    /**
     * Throws an {@link IllegalArgumentException} if the given group element is not a member of the bilinear map
     * signature group
     *
     * @param bilinearMap  the bilinear map the group is part of
     * @param groupElement the group element to assert membership of
     * @param elementName  the name of the element, to record errors
     */
    public static void throwIfNotSignatureGroup(
            @NonNull final BilinearMap bilinearMap,
            @NonNull final GroupElement groupElement,
            @NonNull final String elementName) {

        Objects.requireNonNull(bilinearMap, "bilinearMap must not be null");
        Objects.requireNonNull(groupElement, "groupElement must not be null");
        Objects.requireNonNull(elementName, "elementName must not be null");

        if (!(groupElement
                .group()
                .getClass()
                .equals(bilinearMap.signatureGroup().getClass()))) {
            throw new IllegalArgumentException(
                    String.format("%s must be in the signature group of the bilinear map", elementName));
        }
    }

    /**
     * Throws an {@link IllegalArgumentException} if the given group element is not a member of the bilinear map public
     * key group
     *
     * @param bilinearMap  the bilinear map the group is part of
     * @param groupElement the group element to assert membership of
     * @param elementName  the name of the element, to record errors
     */
    public static void throwIfNotPublicKeyGroup(
            @NonNull final BilinearMap bilinearMap,
            @NonNull final GroupElement groupElement,
            @NonNull final String elementName) {

        Objects.requireNonNull(bilinearMap, "bilinearMap must not be null");
        Objects.requireNonNull(groupElement, "groupElement must not be null");
        Objects.requireNonNull(elementName, "elementName must not be null");

        if (!(groupElement.group().getClass().equals(bilinearMap.keyGroup().getClass()))) {
            throw new IllegalArgumentException(
                    String.format("%s must be in the public key group of the bilinear map", elementName));
        }
    }
}
