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

package com.swirlds.platform.hcm.impl.tss.groth21;

import com.swirlds.platform.hcm.api.pairings.FieldElement;
import com.swirlds.platform.hcm.api.tss.TssShareClaim;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An unencrypted share, created for a particular {@link TssShareClaim}.
 *
 * @param shareClaim   the share claim that this share is for
 * @param shareElement the unencrypted share element
 */
public record Groth21UnencryptedShare(@NonNull TssShareClaim shareClaim, @NonNull FieldElement shareElement) {
    public static Groth21UnencryptedShare create(
            @NonNull final TssShareClaim shareClaim, @NonNull final DensePolynomial polynomial) {
        return new Groth21UnencryptedShare(
                shareClaim, polynomial.evaluate(shareClaim.shareId().idElement()));
    }
}
