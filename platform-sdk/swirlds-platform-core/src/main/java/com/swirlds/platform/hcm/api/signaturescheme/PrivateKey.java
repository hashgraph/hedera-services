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
import com.swirlds.platform.hcm.api.pairings.CurveType;
import com.swirlds.platform.hcm.api.pairings.FieldElement;
import com.swirlds.platform.hcm.impl.internal.SignatureSchema;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.SecureRandom;

/**
 * A private key that can be used to sign a message.
 *
 */
public record PrivateKey(FieldElement element) implements ByteRepresentable {

    /**
     * Sign a message using the private key.
     *
     * @param message the message to sign
     * @return the signature
     */
    @NonNull
    public Signature sign(@NonNull final byte[] message) {
        return SignatureSchema.forPairing(element.curveType()).sign(message, this);
    }

    /**
     * Creates a private key out of the CurveType and a random
     * @param type The implementing curve type
     * @param random The environment secureRandom to use
     * @return a privateKey for that CurveType
     */
    @NonNull
    static PrivateKey create(@NonNull final CurveType type, @NonNull final SecureRandom random) {
        return SignatureSchema.forPairing(type).createKeyPair(random).key();
    }

    /**
     * Creates a PublicKey for this PrivateKey instance
     * @return a new publicKey associated to this PrivateKey
     */
    @NonNull
    public PublicKey createPublicKey() {
        return SignatureSchema.forPairing(element.curveType()).createPublicKey(element);
    }

    /**
     * Deserialize a private key from a byte array.
     *
     * @param bytes the serialized private key
     * @return the deserialized private key
     */
    @NonNull
    public PrivateKey fromBytes(final byte[] bytes) {
        return SignatureSchema.deserializePrivateKey(bytes);
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
        return element.toBytes();
    }
}
