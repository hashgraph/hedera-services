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

import com.swirlds.platform.hcm.api.pairings.ByteRepresentable;
import com.swirlds.platform.hcm.api.pairings.Field;
import com.swirlds.platform.hcm.api.pairings.FieldElement;
import com.swirlds.platform.hcm.api.pairings.Group;
import com.swirlds.platform.hcm.api.pairings.SignatureSchema;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Random;

/**
 * A private key that can be used to sign a message.
 */
public record PairingPrivateKey(@NonNull FieldElement secretElement) implements ByteRepresentable {

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
     * @param publicKey the public key that corresponds to this private key. This is needed, so that we know which
     *                  group to use for the signature
     * @param message   the message to sign
     * @return the signature, which will be in the group opposite to the group of the public key
     */
    @NonNull
    public PairingSignature sign(@NonNull final PairingPublicKey publicKey, @NonNull final byte[] message) {
        final Group publicKeyGroup = publicKey.element().getGroup();
        final Group signatureGroup = publicKeyGroup.getOppositeGroup();

        return new PairingSignature(signatureGroup.elementFromHash(message).power(secretElement));
    }

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
        final byte[] seed = new byte[field.getSeedSize()];
        random.nextBytes(seed);

        return new PairingPrivateKey(field.randomElement(seed));
    }

    /**
     * Deserialize a private key from a byte array.
     *
     * @param bytes the serialized private key
     * @return the deserialized private key
     */
    @NonNull
    public static PairingPrivateKey fromBytes(final @NonNull byte[] bytes) {
        Objects.requireNonNull(bytes);

        if (bytes.length == 0) {
            throw new IllegalArgumentException("Bytes cannot be empty");
        }

        final SignatureSchema curveType = SignatureSchema.fromIdByte(bytes[0]);
        // TODO: do we actually want the elementFromBytes method to have to ignore the curve type byte?
        return new PairingPrivateKey(curveType.getField().elementFromBytes(bytes));
    }

    /**
     * Serialize the private key to a byte array.
     * <p>
     * The first byte of the serialized private key must represent the curve type.
     *
     * @return the serialized private key
     */
    @Override
    @NonNull
    public byte[] toBytes() {
        return secretElement.toBytes();
    }
}
