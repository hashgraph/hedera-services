/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.base.state;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Describes an object that may be mutable or immutable.
 */
public interface Mutable {

    /**
     * Determines if an object is immutable or not.
     *
     * @return Whether is immutable or not
     */
    boolean isImmutable();

    /**
     * Determines if an object is mutable or not.
     *
     * @return Whether the object is immutable or not
     */
    default boolean isMutable() {
        return !isImmutable();
    }

    /**
     * Throws a {@link MutabilityException} if the object is immutable.
     *
     * @throws MutabilityException if {@link #isImmutable()}} returns {@code true}
     */
    default void throwIfImmutable() {
        throwIfImmutable("This operation is not permitted on an immutable object.");
    }

    /**
     * Throws a {@link MutabilityException} if the object is immutable.
     *
     * @param errorMessage an error message for the exception
     * @throws MutabilityException if {@link #isImmutable()}} returns {@code true}
     */
    default void throwIfImmutable(@NonNull final String errorMessage) {
        if (isImmutable()) {
            throw new MutabilityException(errorMessage);
        }
    }

    /**
     * Throws a {@link MutabilityException} if the object is mutable.
     *
     * @throws MutabilityException if {@link #isMutable()} returns {@code true}
     */
    default void throwIfMutable() {
        throwIfMutable("This operation is not permitted on a mutable object.");
    }

    /**
     * Throws a {@link MutabilityException} if the object is mutable.
     *
     * @param errorMessage an error message for the exception
     * @throws MutabilityException if {@link #isMutable()}} returns {@code true}
     */
    default void throwIfMutable(@NonNull final String errorMessage) {
        if (isMutable()) {
            throw new MutabilityException(errorMessage);
        }
    }
}
