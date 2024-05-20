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
import com.swirlds.platform.tss.bls.bls12381.Bls12381Group2;
import com.swirlds.platform.tss.bls.bls12381.g1pk_g2sig.Bls12381G2Signature;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A signature that has been produced by a {@link PrivateKey}.
 *
 * @param <P> the type of public key that can be used to verify this signature
 */
public interface Signature<P extends PublicKey> {
    /**
     * Deserialize a signature from a byte array.
     *
     * @param bytes the serialized signature, with the curve type being represented by the first byte
     * @return the deserialized signature
     */
    static Signature<?> deserialize(final byte[] bytes) {
        // the first byte is id of the curve
        final byte curve = bytes[0];
        // the second byte is the group the element is in
        final byte group = bytes[1];

        switch (curve) {
            case Bls12381Curve.ID_BYTE:
                if (group == 2) {
                    return new Bls12381G2Signature(Bls12381Group2.getInstance().deserializeElementFromBytes(bytes));
                } else {
                    throw new UnsupportedOperationException("Only group 2 signatures are currently supported");
                }
            default:
                throw new UnsupportedOperationException("Unknown curve");
        }
    }

    /**
     * Verify a signed message with the known public key.
     *
     * @param publicKey the public key to verify with
     * @param message   the message that was signed
     * @return true if the signature is valid, false otherwise
     */
    boolean verifySignature(@NonNull final P publicKey, @NonNull final byte[] message);

    /**
     * Serialize the signature to a byte array.
     * <p>
     * The first byte of the serialized signature must represent the curve type.
     *
     * @return the serialized signature
     */
    byte[] serialize();
}
