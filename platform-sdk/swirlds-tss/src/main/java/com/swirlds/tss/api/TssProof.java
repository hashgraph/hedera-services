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

package com.swirlds.tss.api;

import com.swirlds.tss.impl.groth21.FeldmanCommitment;
import com.swirlds.tss.impl.groth21.MultishareCiphertext;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A TSS proof.
 */
public interface TssProof {
    /**
     * Verify this proof.
     *
     * @param ciphertext         the ciphertext that this proof is for
     * @param commitment         the commitment that was made to the ciphertext // TODO: check correctness of this
     *                           description
     * @param pendingShareClaims the pending share claims the TSS message was created for
     * @return true if the proof is valid, false otherwise
     */
    boolean verify(
            @NonNull final MultishareCiphertext ciphertext,
            @NonNull final FeldmanCommitment commitment,
            @NonNull final ShareClaims pendingShareClaims);
}
