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

package com.hedera.node.app.tss.cryptography.tss.api;

import com.hedera.node.app.tss.cryptography.bls.BlsSignature;
import com.hedera.node.app.tss.cryptography.bls.SignatureSchema;
import com.hedera.node.app.tss.cryptography.pairings.api.FieldElement;
import com.hedera.node.app.tss.cryptography.pairings.api.GroupElement;
import com.hedera.node.app.tss.cryptography.tss.extensions.Lagrange;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Represents a partial signature created from share of a secret key.
 * It's a BLS signature with an owner.
 *
 * @param shareId the share ID
 * @param signature the signature
 */
public record TssShareSignature(@NonNull Integer shareId, @NonNull BlsSignature signature) {
    /**
     * Constructor.
     *
     * @param shareId   the share ID
     * @param signature the signature
     */
    public TssShareSignature {
        requireNonNull(shareId, "shareId must not be null");
        requireNonNull(signature, "signature must not be null");
    }

    /**
     * verifies a signature using.
     *
     * @param publicShare the publicShare to verify the signature represented by this instance
     * @param message the signed message
     * @return if the privateKey is valid.
     */
    public boolean verify(@NonNull final TssPublicShare publicShare, @NonNull final byte[] message) {
        Objects.requireNonNull(publicShare, "publicShare must not be null");
        return this.signature.verify(publicShare.publicKey(), message);
    }

    /**
     * Aggregate a threshold number of {@link TssShareSignature}s.
     * It is the responsibility of the caller to ensure that the list of partial signatures meets the required
     * threshold. If the threshold is not met, the privateKey returned by this method will be invalid.
     *
     * @param partialSignatures the list of signatures to aggregate
     * @return the interpolated privateKey
     */
    @NonNull
    public static BlsSignature aggregate(@NonNull List<TssShareSignature> partialSignatures) {
        if (Objects.requireNonNull(partialSignatures, "partialSignatures must not be null")
                        .size()
                < 2) {
            throw new IllegalArgumentException("Not enough partialSignatures to aggregate");
        }
        final Collection<SignatureSchema> s = partialSignatures.stream()
                .map(TssShareSignature::signature)
                .map(BlsSignature::signatureSchema)
                .collect(Collectors.toSet());
        if (s.size() > 1) {
            throw new IllegalArgumentException("publicKeys must not contain more than one signatureSchema");
        }
        final SignatureSchema signatureSchema = s.stream().findFirst().orElseThrow();
        final List<FieldElement> xs = partialSignatures.stream()
                .map(TssShareSignature::shareId)
                .map(signatureSchema.getPairingFriendlyCurve().field()::fromLong)
                .toList();
        final List<GroupElement> ys = partialSignatures.stream()
                .map(TssShareSignature::signature)
                .map(BlsSignature::element)
                .toList();
        return new BlsSignature(Lagrange.recoverGroupElement(xs, ys), signatureSchema);
    }
}
