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

package com.swirlds.platform.hcm.impl.tss.groth21;

import static com.swirlds.platform.hcm.api.tss.TssUtils.computeLagrangeCoefficient;

import com.swirlds.platform.hcm.api.pairings.Field;
import com.swirlds.platform.hcm.api.pairings.FieldElement;
import com.swirlds.platform.hcm.api.pairings.Group;
import com.swirlds.platform.hcm.api.pairings.GroupElement;
import com.swirlds.platform.hcm.api.signaturescheme.PairingPrivateKey;
import com.swirlds.platform.hcm.api.signaturescheme.PairingPublicKey;
import com.swirlds.platform.hcm.api.signaturescheme.PairingSignature;
import com.swirlds.platform.hcm.api.signaturescheme.SignatureSchema;
import com.swirlds.platform.hcm.api.tss.Tss;
import com.swirlds.platform.hcm.api.tss.TssMessage;
import com.swirlds.platform.hcm.api.tss.TssPrivateShare;
import com.swirlds.platform.hcm.api.tss.TssPublicShare;
import com.swirlds.platform.hcm.api.tss.TssShareClaim;
import com.swirlds.platform.hcm.api.tss.TssShareSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A Groth21 implementation of a Threshold Signature Scheme.
 */
public record Groth21Tss(@NonNull SignatureSchema signatureSchema) implements Tss {
    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PairingSignature aggregateSignatures(@NonNull final List<TssShareSignature> partialSignatures) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PairingPublicKey aggregatePublicShares(@NonNull final List<TssPublicShare> publicShares) {
        if (publicShares.isEmpty()) {
            throw new IllegalArgumentException("At least one public share is required to recover a public key");
        }

        final List<FieldElement> shareIds = new ArrayList<>();
        publicShares.forEach(share -> {
            shareIds.add(share.shareId().id());
        });

        final List<FieldElement> lagrangeCoefficients = new ArrayList<>();
        for (int i = 0; i < publicShares.size(); i++) {
            lagrangeCoefficients.add(computeLagrangeCoefficient(shareIds, i));
        }

        final Group group = publicShares.getFirst().publicKey().keyElement().getGroup();

        // TODO: the rust code has this being a sum, but my previous interface definition didn't include a zero group
        //  element. Is this another case of different operation definitions, or does group need a 0 element in
        //  addition to a 1 element?
        GroupElement product = group.oneElement();
        for (int i = 0; i < lagrangeCoefficients.size(); i++) {
            product = product.multiply(
                    publicShares.get(i).publicKey().keyElement().power(lagrangeCoefficients.get(i)));
        }

        return new PairingPublicKey(signatureSchema, product);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PairingPrivateKey aggregatePrivateShares(@NonNull final List<TssPrivateShare> privateShares) {
        if (privateShares.isEmpty()) {
            throw new IllegalArgumentException("At least one private share is required to recover a secret");
        }

        final List<FieldElement> shareIds = new ArrayList<>();
        final List<FieldElement> privateKeys = new ArrayList<>();
        privateShares.forEach(share -> {
            shareIds.add(share.shareId().id());
            privateKeys.add(share.privateKey().secretElement());
        });

        if (shareIds.size() != Set.of(shareIds).size()) {
            throw new IllegalArgumentException("x-coordinates must be distinct");
        }

        final List<FieldElement> lagrangeCoefficients = new ArrayList<>();
        for (int i = 0; i < shareIds.size(); i++) {
            lagrangeCoefficients.add(computeLagrangeCoefficient(shareIds, i));
        }

        final Field field = shareIds.getFirst().getField();
        FieldElement sum = field.zeroElement();
        for (int i = 0; i < lagrangeCoefficients.size(); i++) {
            sum = sum.add(lagrangeCoefficients.get(i).multiply(privateKeys.get(i)));
        }

        return new PairingPrivateKey(signatureSchema, sum);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public TssMessage generateTssMessage(
            @NonNull final List<TssShareClaim> pendingShareClaims,
            @NonNull final TssPrivateShare privateShare,
            final int threshold) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public SignatureSchema getSignatureSchema() {
        return signatureSchema;
    }
}
