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

package com.swirlds.platform.hcm.api.tss;

import com.swirlds.platform.hcm.api.signaturescheme.SignatureSchema;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Random;

/**
 * A Threshold Signature Scheme.
 * <p>
 * Contract of TSS:
 * <ul>
 *     <li>produce a public key for each share</li>
 *     <li>give the corresponding secret to the shareholder</li>
 * </ul>
 */
public interface Tss {
    /**
     * Generate a TSS message for a set of share claims, from a private share.
     *
     * @param random             a source of randomness
     * @param signatureSchema    the signature schema to use
     * @param pendingShareClaims the share claims that we should generate the message for
     * @param privateShare       the secret to use for generating new keys
     * @param threshold          the threshold for recovering the secret
     * @return the TSS message produced for the input share claims
     */
    @NonNull
    TssMessage generateTssMessage(
            @NonNull final Random random,
            @NonNull final SignatureSchema signatureSchema,
            @NonNull final ShareClaims pendingShareClaims,
            @NonNull final TssPrivateShare privateShare,
            final int threshold);

    /**
     * Get the signature schema used by this TSS instance
     *
     * @return the signature schema
     */
    @NonNull
    SignatureSchema getSignatureSchema();
}
