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

import com.swirlds.platform.hcm.api.pairings.Field;
import com.swirlds.platform.hcm.api.pairings.FieldElement;
import com.swirlds.platform.hcm.api.signaturescheme.PairingPrivateKey;
import com.swirlds.platform.hcm.api.signaturescheme.PairingPublicKey;
import com.swirlds.platform.hcm.api.signaturescheme.PairingSignature;
import com.swirlds.platform.hcm.api.signaturescheme.SignatureSchema;
import com.swirlds.platform.hcm.api.tss.Tss;
import com.swirlds.platform.hcm.api.tss.TssMessage;
import com.swirlds.platform.hcm.api.tss.TssPrivateKey;
import com.swirlds.platform.hcm.api.tss.TssPrivateShare;
import com.swirlds.platform.hcm.api.tss.TssPublicShare;
import com.swirlds.platform.hcm.api.tss.TssShareClaim;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.swirlds.platform.hcm.api.tss.TssUtils.computeLagrangeCoefficient;

/**
 * A Groth21 implementation of a Threshold Signature Scheme.
 */
public record Groth21Tss(@NonNull SignatureSchema signatureSchema) implements Tss {
    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public PairingSignature aggregateSignatures(@NonNull final List<PairingSignature> partialSignatures) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public PairingPublicKey aggregatePublicShares(@NonNull final List<TssPublicShare> publicShares) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public TssPrivateKey aggregatePrivateShares(@NonNull final List<TssPrivateShare> privateShares) {
        if (privateShares.isEmpty()) {
            throw new IllegalArgumentException("At least one private share is required to recover a secret");
        }

        final List<FieldElement> xCoordinates = new ArrayList<>();
        final List<FieldElement> yCoordinates = new ArrayList<>();
        privateShares.forEach(share -> {
            xCoordinates.add(share.shareId().id());
            yCoordinates.add(share.privateKey().privateKey().secretElement());
        });

        if (xCoordinates.size() != Set.of(xCoordinates).size()) {
            throw new IllegalArgumentException("x-coordinates must be distinct");
        }

        final List<FieldElement> lagrangeCoefficients = new ArrayList<>();
        for (int i = 0; i < xCoordinates.size(); i++) {
            lagrangeCoefficients.add(computeLagrangeCoefficient(xCoordinates, i));
        }

        final Field field = xCoordinates.getFirst().getField();
        FieldElement sum = field.zeroElement();
        for (int i = 0; i < lagrangeCoefficients.size(); i++) {
            sum = sum.add(lagrangeCoefficients.get(i).multiply(yCoordinates.get(i)));
        }

        return new TssPrivateKey(new PairingPrivateKey(signatureSchema, sum));
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
}
