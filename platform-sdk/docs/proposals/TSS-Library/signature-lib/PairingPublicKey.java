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

import com.hedera.cryptography.pairings.api.GroupElement;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A public key that can be used to verify a signature.
 *
 * @param signatureSchema the signature schema
 * @param keyElement      the public key element
 */
public record PairingPublicKey(@NonNull SignatureSchema signatureSchema, @NonNull GroupElement keyElement) {
    /**
     * Create a public key from a private key.
     *
     * @param privateKey the private key
     * @return the public key
     */
    public static PairingPublicKey create(@NonNull final PairingPrivateKey privateKey) {
        return new PairingPublicKey(
                privateKey.signatureSchema(),
                privateKey.signatureSchema().getPublicKeyGroup().getGenerator().multiply(privateKey.secretElement()));
    }

    /**
     * Deserialize a public key from a byte array.
     *
     * @param bytes the serialized public key, with the first byte representing the curve type
     * @return the deserialized public key
     */
    public static PairingPublicKey fromBytes(@NonNull final byte[] bytes) {
        final SerializedSignatureSchemaObject schemaObject = SerializedSignatureSchemaObject.fromByteArray(bytes);

        return new PairingPublicKey(
                schemaObject.schema(),
                schemaObject.schema().getPublicKeyGroup().elementFromBytes(schemaObject.elementBytes()));
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
        final int elementSize = keyElement.size();
        final byte[] output = new byte[1 + elementSize];

        output[0] = signatureSchema.getIdByte();
        System.arraycopy(keyElement.toBytes(), 0, output, 1, elementSize);

        return output;
    }
}
