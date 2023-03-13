/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common;

import com.swirlds.common.exceptions.MutabilityException;

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
     * @throws MutabilityException
     * 		if {@link #isImmutable()}} returns {@code true}
     */
    default void throwIfImmutable() {
        throwIfImmutable("This operation is not permitted on an immutable object.");
    }

    /**
     * @param errorMessage
     * 		an error message for the exception
     * @throws MutabilityException
     * 		if {@link #isImmutable()}} returns {@code true}
     */
    default void throwIfImmutable(final String errorMessage) {
        if (isImmutable()) {
            throw new MutabilityException(errorMessage);
        }
    }

    /**
     * @throws MutabilityException
     * 		if {@link #isMutable()} returns {@code true}
     */
    default void throwIfMutable() {
        throwIfMutable("This operation is not permitted on a mutable object.");
    }

    /**
     * @param errorMessage
     * 		an error message for the exception
     * @throws MutabilityException
     * 		if {@link #isMutable()}} returns {@code true}
     */
    default void throwIfMutable(final String errorMessage) {
        if (isMutable()) {
            throw new MutabilityException(errorMessage);
        }
    }
}
