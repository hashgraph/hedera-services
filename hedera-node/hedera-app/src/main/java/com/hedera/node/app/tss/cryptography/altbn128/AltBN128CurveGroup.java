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

package com.hedera.node.app.tss.cryptography.altbn128;

/**
 *  Represents the group in the curve
 */
public enum AltBN128CurveGroup {

    /**
     *  G1
     */
    GROUP1(0),
    /**
     *  G2
     */
    GROUP2(1);

    private final int id;

    /**
     * Creates a new instance and sets the id.
     * @param id the id of the group
     */
    AltBN128CurveGroup(final int id) {
        this.id = id;
    }

    /**
     * Return the group id.
     * @return the group id
     */
    public int getId() {
        return id;
    }
}
