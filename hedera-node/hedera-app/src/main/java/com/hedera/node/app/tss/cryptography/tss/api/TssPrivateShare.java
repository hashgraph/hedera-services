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

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.tss.cryptography.bls.BlsPrivateKey;
import com.hedera.node.app.tss.cryptography.bls.SignatureSchema;
import com.hedera.node.app.tss.cryptography.tss.extensions.Lagrange;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a secret portion of a shared key.
 * It's a BLS private key with an owner.
 *
 * @param shareId the share ID
 * @param privateKey the private key
 */
public record TssPrivateShare(@NonNull Integer shareId, @NonNull BlsPrivateKey privateKey) {
    /**
     * Constructor
     *
     * @param shareId the share ID
     * @param privateKey the private key
     */
    public TssPrivateShare {
        requireNonNull(shareId, "shareId must not be null");
        requireNonNull(privateKey, "privateKey must not be null");
    }

    /**
     * Sign a message using the private share's key.
     * @param message the message to sign
     * @return the {@link TssShareSignature}
     */
    @NonNull
    public TssShareSignature sign(@NonNull final byte[] message) {
        return new TssShareSignature(this.shareId(), this.privateKey().sign(message));
    }

    /**
     * Aggregate a threshold number of {@link TssPrivateShare}s.
     * It is the responsibility of the caller to ensure that the list of private shares meets the required threshold.
     * If the threshold is not met, the public key returned by this method will be invalid.
     * This method is used for two distinct purposes:
     * <ul>
     *     <li>Aggregating private shares derived from all commitments, to produce the private key for a given share</li>
     * </ul>
     *
     * @param privateShares the privateShare to aggregate
     * @return the interpolated public key
     */
    @NonNull
    public static BlsPrivateKey aggregate(@NonNull final List<TssPrivateShare> privateShares) {
        if (Objects.requireNonNull(privateShares, "privateShares must not be null")
                        .size()
                < 2) {
            throw new IllegalArgumentException("Not enough privateShares to aggregate");
        }
        final Collection<SignatureSchema> s = privateShares.stream()
                .map(TssPrivateShare::privateKey)
                .map(BlsPrivateKey::signatureSchema)
                .collect(Collectors.toSet());
        if (s.size() > 1) {
            throw new IllegalArgumentException("privateShares must not contain more than one signatureSchema");
        }
        final SignatureSchema signatureSchema = s.stream().findFirst().orElseThrow();
        var xs = privateShares.stream()
                .map(TssPrivateShare::shareId)
                .map(signatureSchema.getPairingFriendlyCurve().field()::fromLong)
                .toList();
        var ys = privateShares.stream()
                .map(TssPrivateShare::privateKey)
                .map(BlsPrivateKey::element)
                .toList();
        return new BlsPrivateKey(Lagrange.recoverFieldElement(xs, ys), signatureSchema);
    }
}
