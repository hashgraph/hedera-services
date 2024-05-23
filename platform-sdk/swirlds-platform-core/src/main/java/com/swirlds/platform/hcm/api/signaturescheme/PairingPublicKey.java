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
import com.swirlds.platform.hcm.api.pairings.GroupElement;
import com.swirlds.platform.hcm.api.pairings.SignatureSchema;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * A public key that can be used to verify a signature.
 */
public record PairingPublicKey(@NonNull GroupElement element) implements ByteRepresentable {
    /**
     * Create a public key from a private key.
     *
     * @param signingCurveType the signing curve type
     * @param privateKey       the private key
     * @return the public key
     */
    public static PairingPublicKey create(
            @NonNull final SignatureSchema signingCurveType, @NonNull final PairingPrivateKey privateKey) {
        return new PairingPublicKey(
                signingCurveType.getPublicKeyGroup().getGenerator().power(privateKey.secretElement()));
    }

    /**
     * Deserialize a public key from a byte array.
     *
     * @param bytes the serialized public key, with the curve type being represented by the first byte
     * @return the deserialized public key
     */
    public static PairingPublicKey fromBytes(final @NonNull byte[] bytes) {
        Objects.requireNonNull(bytes);

        if (bytes.length == 0) {
            throw new IllegalArgumentException("Bytes cannot be empty");
        }

        final SignatureSchema curveType = SignatureSchema.fromIdByte(bytes[0]);
        // TODO: do we actually want the elementFromBytes method to have to ignore the curve type byte?
        return new PairingPublicKey(curveType.getPublicKeyGroup().elementFromBytes(bytes));
    }

    /**
     * Serialize the public key to a byte array.
     * <p>
     * The first byte of the serialized public key must represent the curve type.
     *
     * @return the serialized public key
     */
    @NonNull
    public byte[] toBytes() {
        return element().toBytes();
    }
}
