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

package com.swirlds.crypto.signaturescheme.api;

import com.swirlds.crypto.pairings.api.Curve;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents a threshold signature schema.
 *
 * @implNote Given that we pack the type of the curve in serialized forms in 1 byte alongside other information
 * we can only support a limited amount of curves (128).
 */
public class SignatureSchema {
    private GroupAssignment groupAssignment;
    private Curve cure;

    public SignatureSchema(GroupAssignment groupAssignment, Curve cure) {
        this.groupAssignment = groupAssignment;
        this.cure = cure;
    }

    /**
     * Returns a signature scheme a curve and a groupAssignment
     *
     * @param groupAssignment the group assignment
     * @param curve           the curve
     * @return the SignatureSchema instance
     */
    @NonNull
    public static SignatureSchema create(@NonNull Curve curve, @NonNull GroupAssignment groupAssignment) {
        return new SignatureSchema(groupAssignment, curve);
    }
}
