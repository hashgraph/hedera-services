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

package com.swirlds.platform.tss.bls;

/**
 * Interface representing a cryptographic group element
 *
 * TODO: this is a temporary placeholder, until we have the BLS library ready for use
 */
public interface GroupElement {
    /**
     * Returns the group of the element
     *
     * @return the element's group
     */
    Group group();

    /**
     * Serializes the group elements to a byte array
     *
     * @return the byte array representing the group element
     */
    byte[] toBytes();

    /**
     * Takes the group element to the power of a field element
     *
     * @param exponent the field element exponent
     * @return a new group element which is this group element to the power of a field element
     */
    GroupElement power(FieldElement exponent);

    /**
     * Multiplies this group element with another
     *
     * @param other the other group element
     * @return a new group element which is the product of this element and another
     */
    GroupElement multiply(GroupElement other);

    /**
     * Divides this group element by another
     *
     * @param other the other group element
     * @return a new group element which is the quotient of this element and another
     */
    GroupElement divide(GroupElement other);

    /**
     * Compresses the group element
     *
     * @return this object, compressed
     */
    GroupElement compress();

    /**
     * Gets whether the group element is compressed
     *
     * @return true if the element is compressed, otherwise false
     */
    boolean isCompressed();

    /**
     * {@inheritDoc}
     */
    GroupElement copy();

    /**
     * Checks whether the element bytes are valid
     *
     * @return true of the element bytes are valid, otherwise false
     */
    boolean isValid();

    @Override
    String toString();
}
