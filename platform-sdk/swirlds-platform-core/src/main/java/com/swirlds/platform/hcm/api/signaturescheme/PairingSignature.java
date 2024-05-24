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

package com.swirlds.platform.hcm.api.signaturescheme;

import com.swirlds.platform.hcm.api.pairings.BilinearPairing;
import com.swirlds.platform.hcm.api.pairings.Group;
import com.swirlds.platform.hcm.api.pairings.GroupElement;
import com.swirlds.platform.hcm.api.pairings.PairingResult;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * A signature that has been produced by a {@link PairingPrivateKey}.
 */
public record PairingSignature(@NonNull GroupElement signatureElement) {
    /**
     * Deserialize a signature from a byte array.
     *
     * @param bytes the serialized signature, with the curve type being represented by the first byte
     * @return the deserialized signature
     */
    public static PairingSignature fromBytes(final @NonNull byte[] bytes) {
        Objects.requireNonNull(bytes);

        if (bytes.length == 0) {
            throw new IllegalArgumentException("Bytes cannot be empty");
        }

        final SignatureSchema curveType = SignatureSchema.fromIdByte(bytes[0]);
        // TODO: do we actually want the elementFromBytes method to have to ignore the curve type byte?
        return new PairingSignature(curveType.getSignatureGroup().elementFromBytes(bytes));
    }

    /**
     * Verify a signed message with the known public key.
     * <p>
     * To verify a signature, we need to ensure that the message m was signed with the corresponding private key “sk”
     * for the given public key “pk”.
     * <p>
     * The signature is considered valid only if the pairing between the generator of the public key group and the
     * signature “σ” is equal to the pairing between the public key and the message hashed to the signature key group.
     * <p>
     * Mathematically, this verification can be expressed like this:
     * e(pk, H(m)) = e([sk]g1, H(m)) = e(g1, H(m))^(sk) = e(g1, [sk]H(m)) = e(g1, σ).
     *
     * @param publicKey the public key to verify with
     * @param message   the message that was signed
     * @return true if the signature is valid, false otherwise
     */
    public boolean verifySignature(@NonNull final PairingPublicKey publicKey, @NonNull final byte[] message) {
        final Group publicKeyGroup = publicKey.element().getGroup();
        final Group signatureGroup = publicKeyGroup.getOppositeGroup();
        final BilinearPairing pairing = publicKeyGroup.getPairing();
        final GroupElement messageHashedToGroup = signatureGroup.elementFromHash(message);

        final PairingResult lhs = pairing.pairingBetween(signatureElement, publicKeyGroup.getGenerator());
        final PairingResult rhs = pairing.pairingBetween(messageHashedToGroup, publicKey.element());

        return pairing.comparePairingResults(lhs, rhs);
    }

    /**
     * Serialize the signature to a byte array.
     * <p>
     * The first byte of the serialized signature must represent the curve type.
     *
     * @return the serialized signature
     */
    @NonNull
    public byte[] toBytes() {
        return signatureElement.toBytes();
    }
}
