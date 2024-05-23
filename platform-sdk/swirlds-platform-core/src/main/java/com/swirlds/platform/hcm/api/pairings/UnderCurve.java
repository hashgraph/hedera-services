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

package com.swirlds.platform.hcm.api.pairings;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents an interface for objects that operate under a specific elliptic curve type in cryptographic operations.
 *
 * <p>This interface defines methods to retrieve the type of curve and provides default methods to check that
 * operations are performed on objects of the same curve type.</p>
 *
 * @see CurveType
 */
public interface UnderCurve {

    /**
     * Retrieves the type of the elliptic curve.
     *
     * @return the type of the elliptic curve.
     */
    @NonNull
    CurveType curveType();

    /**
     * Retrieves the ID of the elliptic curve type as a byte
     *
     * @return the ID of the elliptic curve type as a byte
     */
    default byte curveTypeId() {
        return curveType().getId();
    }

    /**
     * Checks if another {@code UnderCurve} belongs to the same curve type and throws an exception if not.
     *
     * @param other the other element to check
     */
    default void checkSameCurveType(@NonNull final UnderCurve other) {
        if (curveType() != other.curveType()) {
            throw new IllegalStateException("Not implementing the same curve type");
        }
    }

    /**
     * Checks if another {@code UnderCurve} belongs to the same curve type and throws an exception if not.
     *
     * @param serializedOther the serialized version other element to check
     */
    default void checkSameCurveType(final byte[] serializedOther) {
        if (curveTypeId() != serializedOther[0]) {
            throw new IllegalStateException("Not implementing the same curve type");
        }
    }
}
