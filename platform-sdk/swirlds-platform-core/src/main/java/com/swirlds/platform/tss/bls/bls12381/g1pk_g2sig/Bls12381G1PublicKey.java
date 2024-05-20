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

package com.swirlds.platform.tss.bls.bls12381.g1pk_g2sig;

import com.swirlds.platform.tss.bls.bls12381.Bls12381Curve;
import com.swirlds.platform.tss.bls.bls12381.Bls12381Group1Element;
import com.swirlds.platform.tss.verification.PublicKey;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents a BLS12-381 public key in G1.
 *
 * @param keyMaterial the public key material
 */
public record Bls12381G1PublicKey(@NonNull Bls12381Group1Element keyMaterial) implements PublicKey {
    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] serialize() {
        final byte[] serializedKey = keyMaterial.toBytes();

        final byte[] output = new byte[serializedKey.length + 2];
        // byte at index 0 indicates the curve
        output[0] = Bls12381Curve.ID_BYTE;
        // byte at index 1 indicates which group the element is in
        output[1] = (byte) 1;

        // TODO: is there a smarter way to do this?
        System.arraycopy(serializedKey, 0, output, 2, serializedKey.length);

        return output;
    }
}
