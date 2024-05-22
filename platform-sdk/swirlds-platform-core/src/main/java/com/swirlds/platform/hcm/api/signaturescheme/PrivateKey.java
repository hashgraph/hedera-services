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

import com.swirlds.platform.hcm.impl.internal.SignatureSchema;
import com.swirlds.platform.hcm.api.pairings.CurveType;
import com.swirlds.platform.hcm.api.pairings.FieldElement;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A private key that can be used to sign a message.
 *
 */
public record PrivateKey(FieldElement element) {

    public static final char TYPE = 'p';
    public static final int MIN = 0; // Review value... does it exist?

    /**
     * Deserialize a private key from a byte array.
     *
     * @param bytes the serialized private key
     * @return the deserialized private key
     */
    static PrivateKey deserialize(final byte[] bytes) {
        return SignatureSchema.deserializePrivateKey(bytes);
    }

    /**
     * Serialize the private key to a byte array.
     * <p>
     * The first byte of the serialized private key must represent the curve type.
     *
     * @return the serialized private key
     */
    public byte[] serialize() {
        return SignatureSchema.getBytes(TYPE, element().toBytes());
    }

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

    static PrivateKey create(CurveType type) {
        return SignatureSchema.forPairing(type).createKeyPair().key();
    }

    public PublicKey createPublicKey() {
        return SignatureSchema.forPairing(element.curveType()).createPublicKey(element);
    }
}
