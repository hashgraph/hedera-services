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

import com.swirlds.signaturescheme.api.PairingPublicKey;
import com.swirlds.tss.impl.groth21.FeldmanCommitment;
import com.swirlds.tss.impl.groth21.MultishareCiphertext;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A message sent as part of either genesis keying, or rekeying.
 *
 * @param shareId    the ID of the share used to generate this message
 * @param cipherText contains secrets that are being distributed
 * @param commitment a commitment to the polynomial that was used to generate the secrets
 * @param proof      a proof that the polynomial commitment is valid
 */
public record TssMessage(
        @NonNull TssShareId shareId,
        @NonNull MultishareCiphertext cipherText,
        @NonNull FeldmanCommitment commitment,
        @NonNull TssProof proof) {

    /**
     * Verify that the message is valid.
     *
     * @param publicKey   the public key which corresponds to the private key used to generate the message
     * @param shareClaims the pending share claims the TSS message was created for
     * @return true if the message is valid, false otherwise
     */
    public boolean verify(@NonNull final PairingPublicKey publicKey, @NonNull final ShareClaims shareClaims) {
        return publicKey.keyElement().equals(commitment.commitmentCoefficients().getFirst())
                && proof.verify(cipherText, commitment, shareClaims);
    }

    /**
     * Convert the message to a byte array.
     *
     * @return the byte array representation of the message
     */
    public byte[] toBytes() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
