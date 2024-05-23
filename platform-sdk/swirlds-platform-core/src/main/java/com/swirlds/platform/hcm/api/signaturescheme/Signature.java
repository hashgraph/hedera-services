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
import com.swirlds.platform.hcm.impl.internal.SignatureSchema;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A signature that has been produced by a {@link PrivateKey}.
 *
 */
public record Signature(GroupElement element) implements ByteRepresentable {
    /**
     * Deserialize a signature from a byte array.
     *
     * @param bytes the serialized signature, with the curve type being represented by the first byte
     * @return the deserialized signature
     */
    public static Signature fromBytes(final byte[] bytes) {
        return SignatureSchema.deserializeSignature(bytes);
    }

    /**
     * Verify a signed message with the known public key.
     *
     * @param publicKey the public key to verify with
     * @param message   the message that was signed
     * @return true if the signature is valid, false otherwise
     */
    public boolean verifySignature(@NonNull final PublicKey publicKey, @NonNull final byte[] message) {
        return SignatureSchema.forPairing(publicKey.element().curveTypeId()).verifySignature(message, publicKey, this);
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
        return element().toBytes();
    }
}
