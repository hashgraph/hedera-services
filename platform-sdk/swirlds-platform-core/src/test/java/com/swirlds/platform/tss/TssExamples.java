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

package com.swirlds.platform.tss;

import com.swirlds.platform.tss.bls.bls12381.Bls12381Curve;
import com.swirlds.platform.tss.bls.bls12381.Bls12381FieldElement;
import com.swirlds.platform.tss.bls.bls12381.Bls12381Group1Element;
import com.swirlds.platform.tss.bls.bls12381.Bls12381Group2Element;
import com.swirlds.platform.tss.bls.bls12381.g1pk_g2sig.Bls12381G2SigPrivateKey;
import com.swirlds.platform.tss.groth21.Groth21ShareId;
import com.swirlds.platform.tss.pairings.Curve;
import com.swirlds.platform.tss.pairings.GroupElement;
import com.swirlds.platform.tss.verification.PrivateKey;
import com.swirlds.platform.tss.verification.PublicKey;
import com.swirlds.platform.tss.verification.Signature;
import org.junit.jupiter.api.Test;

class TssExamples {
    @Test
    void createTssPrivateKey() {
        // instantiate the curve
        final Curve<Bls12381Curve, Bls12381FieldElement, Bls12381Group1Element, Bls12381Group2Element> curve =
                new Bls12381Curve();

        // generate the cryptographic elements
        final PrivateKey privateKey =
                new Bls12381G2SigPrivateKey(curve.getField().randomElement(new byte[32]));
        final TssShareId shareId = new Groth21ShareId(curve.getField().randomElement(new byte[32]));

        // bundle the cryptographic elements into a TSS object
        final TssPrivateKey tssPrivateKey = new TssPrivateKey(privateKey);
        final Signature signature = tssPrivateKey.sign(shareId, new byte[32]);
    }

    @Test
    void createTssPublicShare() {
        // instantiate the curve
        final Curve<Bls12381Curve, Bls12381FieldElement, Bls12381Group1Element, Bls12381Group2Element> curve =
                new Bls12381Curve();

        // generate the cryptographic elements
        final GroupElement publicKey = curve.getGroup1().randomElement(new byte[32]);
        final TssShareId shareId = new Groth21ShareId(curve.getField().randomElement(new byte[32]));

        // bundle the cryptographic elements into a TSS object
        final TssPublicShare tssPublicShare = new TssPublicShare(shareId, publicKey);
    }

    @Test
    void deserializedObjects() {
        // instantiate the curve
        final Curve<Bls12381Curve, Bls12381FieldElement, Bls12381Group1Element, Bls12381Group2Element> curve =
                new Bls12381Curve();

        // the consensus node serializes to bytes, and places these in the block stream
        final byte[] serializedPublicKey =
                curve.getGroup1().randomElement(new byte[32]).toBytes();
        final byte[] serializedSignature =
                curve.getGroup2().randomElement(new byte[32]).toBytes();

        // the block node deserializes the bytes, and operates on them without having to know specific types
        final PublicKey publicKey = PublicKey.deserialize(serializedPublicKey);
        final Signature signature = Signature.deserialize(serializedSignature);
        final boolean isSignatureValid = signature.verifySignature(publicKey, new byte[32]);
    }
}
