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

package com.swirlds.platform.tss;

import com.swirlds.platform.tss.verification.PrivateKey;
import com.swirlds.platform.tss.verification.PublicKey;
import com.swirlds.platform.tss.verification.Signature;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A TSS private key.
 */
public record TssPrivateKey<P extends PublicKey>(@NonNull PrivateKey<P> privateKey) {
    /**
     * Sign a message using the TSS private key.
     *
     * @param shareId the ID of the share this private key corresponds to
     * @param message the message to sign
     * @return the signature
     */
    @NonNull
    Signature<P> sign(@NonNull TssShareId shareId, @NonNull byte[] message) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
