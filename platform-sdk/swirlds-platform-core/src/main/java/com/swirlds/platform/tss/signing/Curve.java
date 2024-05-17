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

package com.swirlds.platform.tss.signing;

/**
 * An enum representing the curves supported by the signing library
 * <p>
 * Though intended to support different curves, it would also be possible to use this enum to represent the intention
 * to utilize separate underlying libraries to handle a single given curve.
 */
public enum Curve {
    /**
     * The BLS12-381 curve, with signatures in G2
     */
    BLS12_381_G2SIG(0);

    /**
     * Unique ID of the curve
     */
    private final int curveId;

    /**
     * Constructs an enum instance
     *
     * @param curveId unique ID of the instance
     */
    Curve(final int curveId) {
        if (curveId < 0) {
            throw new IllegalArgumentException("ID must be non-negative");
        }
        if (curveId > 255) {
            throw new IllegalArgumentException("ID must be less than 256");
        }
        this.curveId = curveId;
    }

    /**
     * Get the byte representation of the curve
     * <p>
     * The purpose of this method is to allow a serialized instance of an object on the curve to be deserialized,
     * without needing to know in advance what curve the object is on.
     *
     * @return the byte representation of the curve
     */
    public byte toByte() {
        return (byte) curveId;
    }
}
