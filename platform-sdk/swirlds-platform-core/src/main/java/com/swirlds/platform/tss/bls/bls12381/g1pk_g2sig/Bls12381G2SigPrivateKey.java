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
import com.swirlds.platform.tss.bls.bls12381.Bls12381FieldElement;
import com.swirlds.platform.tss.bls.bls12381.Bls12381Group2;
import com.swirlds.platform.tss.verification.PrivateKey;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents a BLS12-381 private key, used for signing into G2.
 *
 * @param keyMaterial
 */
public record Bls12381G2SigPrivateKey(@NonNull Bls12381FieldElement keyMaterial)
        implements PrivateKey<Bls12381G1PublicKey> {
    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] serialize() {
        final byte[] serializedKey = keyMaterial.toBytes();

        final byte[] output = new byte[serializedKey.length + 1];
        output[0] = Bls12381Curve.ID_BYTE;

        // TODO: is there a smarter way to do this?
        System.arraycopy(serializedKey, 0, output, 1, serializedKey.length);

        return output;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Bls12381G2Signature sign(@NonNull final byte[] message) {
        return new Bls12381G2Signature(
                Bls12381Group2.getInstance().hashToGroup(message).power(keyMaterial));
    }
}
