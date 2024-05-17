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

package com.swirlds.platform.tss.bls.bls12381.g2signatures;

import com.swirlds.platform.tss.signing.Curve;
import com.swirlds.platform.tss.signing.Signature;
import com.swirlds.platform.tss.bls.bls12381.Bls12381BilinearMap;
import com.swirlds.platform.tss.bls.bls12381.Bls12381Group1;
import com.swirlds.platform.tss.bls.bls12381.Bls12381Group2;
import com.swirlds.platform.tss.bls.bls12381.Bls12381Group2Element;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents a BLS12-381 signature in G2.
 *
 * @param signature the signature group element
 */
public record Bls12381G2Signature(@NonNull Bls12381Group2Element signature)
        implements Signature<Bls12381G1PublicKey> {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verifySignature(@NonNull final Bls12381G1PublicKey publicKey, @NonNull final byte[] message) {
        return Bls12381BilinearMap.getInstance()
                .comparePairing(
                        Bls12381Group2.getInstance().hashToGroup(message),
                        publicKey.keyMaterial(),
                        signature,
                        Bls12381Group1.getInstance().getGenerator());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] serialize() {
        final byte[] serializedSignature = signature.toBytes();

        final byte[] output = new byte[serializedSignature.length + 1];
        output[0] = (byte) Curve.BLS12_381_G2SIG.ordinal();

        // TODO: is there a smarter way to do this?
        System.arraycopy(serializedSignature, 0, output, 1, serializedSignature.length);

        return output;
    }
}
