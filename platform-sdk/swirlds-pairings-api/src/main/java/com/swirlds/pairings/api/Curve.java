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

public enum Curve {
    BLS_12_381((byte) 0),
    BLS_24_477((byte) 1),
    BLS_48_581((byte) 2);

    final byte id;

    Curve(byte id) {
        this.id = id;
    }

    public byte getId() {
        return id;
    }

    public static Curve fromId(byte curveId) {
        for (Curve curve : values()) {
            if (curve.id == curveId) {
                return curve;
            }
        }
        throw new IllegalArgumentException("Unknown curve id: " + curveId);
    }
}
