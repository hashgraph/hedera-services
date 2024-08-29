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

import com.hedera.cryptography.pairings.api.Field;
import com.hedera.cryptography.pairings.api.FieldElement;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Random;

/**
 * A private key that can be used to sign a message.
 *
 * @param signatureSchema the signature schema
 * @param secretElement   the secret element
 */
public record PairingPrivateKey(@NonNull SignatureSchema signatureSchema, @NonNull FieldElement secretElement) {
    /**
     * Creates a private key out of the CurveType and a random
     *
     * @param type   The implementing curve type
     * @param random The environment secureRandom to use
     * @return a privateKey for that CurveType
     */
    @NonNull
    public static PairingPrivateKey create(@NonNull final SignatureSchema type, @NonNull final Random random) {
        final Field field = type.getField();
        return new PairingPrivateKey(type, field.randomElement(random));
    }

    /**
     * Deserialize a private key from a byte array.
     *
     * @param bytes the serialized private key, with the first byte representing the curve type
     * @return the deserialized private key
     */
    public static PairingPrivateKey fromBytes(@NonNull final byte[] bytes) {
        final SerializedSignatureSchemaObject schemaObject = SerializedSignatureSchemaObject.fromByteArray(bytes);

        return new PairingPrivateKey(
                schemaObject.schema(), schemaObject.schema().getField().elementFromBytes(schemaObject.elementBytes()));
    }

    /**
     * Sign a message using the private key.
     * <p>
     * Signing:
     * In order to sign a message “m”, the first step is to map it onto a point in the signature group.
     * After this step, the resulting point in the signature group is referred to as “H(m)”.
     * <p>
     * The message is signed by computing the signature “σ = [sk]H(m)”, where “[sk]H(m)” represents multiplying the
     * hash point by the private key.
     *
     * @param message the message to sign
     * @return the signature, which will be in the group opposite to the group of the public key
     */
    @NonNull
    public PairingSignature sign(@NonNull final byte[] message) {
        return new PairingSignature(
                signatureSchema,
                signatureSchema.getSignatureGroup().elementFromHash(message).multiply(secretElement));
    }

    /**
     * Serialize the private key to a byte array.
     * <p>
     * The first byte of the serialized private key represents the curve type.
     *
     * @return the serialized private key
     */
    @NonNull
    public byte[] toBytes() {
        final int elementSize = secretElement.size();
        final byte[] output = new byte[1 + elementSize];

        output[0] = signatureSchema.getIdByte();
        System.arraycopy(secretElement.toBytes(), 0, output, 1, elementSize);

        return output;
    }
}
