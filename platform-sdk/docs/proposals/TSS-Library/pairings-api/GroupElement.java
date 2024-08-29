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


package com.hedera.cryptography.pairings.api;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Interface representing a cryptographic group element
 */
public interface GroupElement {
    /**
     * Check if the group of another element is the same as this element's group
     *
     * @param otherElement the other element
     * @return true if the groups are the same, otherwise false
     */
    default boolean isSameGroup(@NonNull final GroupElement otherElement) {
        return otherElement.getGroup().equals(getGroup());
    }

    /**
     * Check if the group of another element is the opposite of this element's group
     *
     * @param otherElement the other element
     * @return true if the groups are the opposite, otherwise false
     */
    default boolean isOppositeGroup(@NonNull final GroupElement otherElement) {
        return getGroup().getOppositeGroup().equals(otherElement.getGroup());
    }

    /**
     * Returns the size of the group element in bytes
     *
     * @return the size of the group element in bytes
     */
    default int size() {
        return isCompressed() ? getGroup().getCompressedSize() : getGroup().getUncompressedSize();
    }

    /**
     * Returns the group of the element
     *
     * @return the element's group
     */
    @NonNull
    Group getGroup();

    /**
     * Multiplies the group element with a field element
     *
     * @param other the field element
     * @return a new group element which is this group element multiplied by the field element
     */
    @NonNull
    GroupElement multiply(@NonNull FieldElement other);

    /**
     * Adds this group element with another
     *
     * @param other the other group element
     * @return a new group element which is the addition of this element and another
     */
    @NonNull
    GroupElement add(@NonNull GroupElement other);

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

    /**
     * Returns the byte array representation of the group element
     *
     * @return the byte array representation of the group element
     */
    @NonNull
    byte[] toBytes();
}
