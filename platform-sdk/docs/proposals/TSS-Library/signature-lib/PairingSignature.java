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

package com.hedera.cryptography.signaturescheme.api;

import com.hedera.cryptography.pairings.api.BilinearPairing;
import com.hedera.cryptography.pairings.api.Group;
import com.hedera.cryptography.pairings.api.GroupElement;
import com.hedera.cryptography.pairings.api.PairingResult;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A signature that has been produced by a {@link PairingPrivateKey}.
 *
 * @param signatureSchema  the schema of the signature
 * @param signatureElement the signature element
 */
public record PairingSignature(@NonNull SignatureSchema signatureSchema, @NonNull GroupElement signatureElement) {
    /**
     * Deserialize a signature from a byte array.
     *
     * @param bytes the serialized signature, with the first byte representing the curve type
     * @return the deserialized signature
     */
    public static PairingSignature fromBytes(@NonNull final byte[] bytes) {
        final SerializedSignatureSchemaObject schemaObject = SerializedSignatureSchemaObject.fromByteArray(bytes);

        return new PairingSignature(
                schemaObject.schema(),
                schemaObject.schema().getSignatureGroup().elementFromBytes(schemaObject.elementBytes()));
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
        final Group publicKeyGroup = publicKey.keyElement().getGroup();
        if (!publicKeyGroup.equals(signatureSchema.getPublicKeyGroup())) {
            throw new IllegalArgumentException("The public key group does not match the signature schema.");
        }

        final Group signatureGroup = signatureElement.getGroup();
        final BilinearPairing pairing = signatureSchema.getPairing();
        final GroupElement messageHashedToGroup = signatureGroup.elementFromHash(message);

        final PairingResult lhs = pairing.pairingBetween(signatureElement, publicKeyGroup.getGenerator());
        final PairingResult rhs = pairing.pairingBetween(messageHashedToGroup, publicKey.keyElement());

        return pairing.comparePairingResults(lhs, rhs);
    }

    /**
     * Serialize the public key to a byte array.
     * <p>
     * The first byte of the serialized public key will represent the curve type.
     *
     * @return the serialized public key
     */
    @NonNull
    public byte[] toBytes() {
        final int elementSize = signatureElement.size();
        final byte[] output = new byte[1 + elementSize];

        output[0] = signatureSchema.getIdByte();
        System.arraycopy(signatureElement.toBytes(), 0, output, 1, elementSize);

        return output;
    }
}
