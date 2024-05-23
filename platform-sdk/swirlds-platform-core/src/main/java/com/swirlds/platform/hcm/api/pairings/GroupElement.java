/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.hcm.api.pairings;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Interface representing a cryptographic group element
 */
public interface GroupElement extends ByteRepresentable, UnderCurve {

    /**
     * Takes the group element to the power of a field element
     *
     * @param exponent the field element exponent
     * @return a new group element which is this group element to the power of a field element
     */
    @NonNull
    GroupElement power(@NonNull FieldElement exponent);

    /**
     * Multiplies this group element with another
     *
     * @param other the other group element
     * @return a new group element which is the product of this element and another
     */
    @NonNull
    GroupElement multiply(@NonNull GroupElement other);

    /**
     * Adds this group element with another
     *
     * @param other the other group element
     * @return a new group element which is the addition of this element and another
     */
    @NonNull
    GroupElement add(@NonNull GroupElement other);

    /**
     * Divides this group element by another
     *
     * @param other the other group element
     * @return a new group element which is the quotient of this element and another
     */
    @NonNull
    GroupElement divide(@NonNull GroupElement other);

    /**
     * Compresses the group element
     *
     * @return this object, compressed
     */
    @NonNull
    GroupElement compress();

    /**
     * Gets whether the group element is compressed
     *
     * @return true if the element is compressed, otherwise false
     */
    boolean isCompressed();

    /**
     * Returns a copy of the group element
     *
     * @return a copy of the group element
     */
    @NonNull
    GroupElement copy();
}
