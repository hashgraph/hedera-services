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

package com.swirlds.pairings.api;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An enumeration of supported pairing curves.
 */
public enum Curve {
    ALT_BN128((byte) 0),
    BLS12_381((byte) 1);

    private final byte id;

    /**
     * Create a new curve with the given id.
     *
     * @param id the curve id
     */
    Curve(final byte id) {
        this.id = id;
    }

    /**
     * Get the curve id byte.
     *
     * @return the curve id byte
     */
    public byte getId() {
        return id;
    }

    /**
     * Get the curve from the curve id.
     *
     * @param curveId the curve id
     * @return the curve
     */
    @NonNull
    public static Curve fromId(final byte curveId) {
        for (final Curve curve : values()) {
            if (curve.id == curveId) {
                return curve;
            }
        }
        throw new IllegalArgumentException("Unknown curve id: " + curveId);
    }
}
