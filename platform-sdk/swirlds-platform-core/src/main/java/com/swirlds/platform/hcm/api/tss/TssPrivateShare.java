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

import com.swirlds.platform.hcm.api.signaturescheme.PairingPublicKey;
import com.swirlds.platform.hcm.api.signaturescheme.PairingSignature;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A record that contains a share ID, and the corresponding private key.
 *
 * @param shareId    the share ID
 * @param privateKey the private key
 * @param <P>        the type of public key that can verify signatures produced by this private share
 */
public record TssPrivateShare<P extends PairingPublicKey>(
        @NonNull TssShareId shareId, @NonNull TssPrivateKey<P> privateKey) {
    /**
     * Sign a message using the private key.
     *
     * @param message the message to sign
     * @return the signature
     */
    @NonNull
    PairingSignature sign(@NonNull final byte[] message) {
        return privateKey.sign(shareId, message);
    }
}
