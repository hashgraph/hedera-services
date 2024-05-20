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

package com.swirlds.platform.tss.verification;

import com.swirlds.platform.tss.bls.bls12381.Bls12381Curve;
import com.swirlds.platform.tss.bls.bls12381.Bls12381Field;
import com.swirlds.platform.tss.bls.bls12381.g1pk_g2sig.Bls12381G2SigPrivateKey;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A private key that can be used to sign a message.
 *
 * @param <P> the type of public key that corresponds to this private key
 */
public interface PrivateKey<P extends PublicKey> {
    /**
     * Deserialize a private key from a byte array.
     *
     * @param bytes the serialized private key
     * @return the deserialized private key
     */
    static PrivateKey<?> deserialize(final byte[] bytes) {
        // the first byte is id of the curve
        final byte curve = bytes[0];
        // the second byte is the group that signatures produced by this private key are in
        final byte signatureGroup = bytes[1];

        switch (curve) {
            case Bls12381Curve.ID_BYTE:
                if (signatureGroup == 2) {
                    return new Bls12381G2SigPrivateKey(
                            Bls12381Field.getInstance().deserializeElementFromBytes(bytes));
                } else {
                    throw new UnsupportedOperationException(
                            "Only private keys that produce signatures in group 2 are currently supported");
                }
            default:
                throw new UnsupportedOperationException("Unknown curve");
        }
    }

    /**
     * Serialize the private key to a byte array.
     * <p>
     * The first byte of the serialized private key must represent the curve type.
     *
     * @return the serialized private key
     */
    byte[] serialize();

    /**
     * Sign a message using the private key.
     *
     * @param message the message to sign
     * @return the signature
     */
    @NonNull
    Signature<P> sign(@NonNull final byte[] message);
}
