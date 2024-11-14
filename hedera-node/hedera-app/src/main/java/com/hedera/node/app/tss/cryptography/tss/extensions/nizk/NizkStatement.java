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

package com.hedera.node.app.tss.cryptography.tss.extensions.nizk;

import com.hedera.node.app.tss.cryptography.bls.BlsPublicKey;
import com.hedera.node.app.tss.cryptography.pairings.api.GroupElement;
import com.hedera.node.app.tss.cryptography.pairings.extensions.EcPolynomial;
import com.hedera.node.app.tss.cryptography.tss.api.TssShareTable;
import com.hedera.node.app.tss.cryptography.tss.extensions.elgamal.CombinedCiphertext;
import com.hedera.node.app.tss.cryptography.utils.HashUtils;
import com.hedera.node.app.tss.cryptography.utils.HashUtils.HashCalculator;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * The public part of a Nizk proof.
 *
 * @param tssShareIds a list of tssIds, should be consecutive and each participantId value should match the index in the list.
 * @param tssEncryptionKeys a Map to retrieve the corresponding tssEncryptionKey of the participant owning the share
 * @param polynomialCommitment a {@link EcPolynomial}
 * @param combinedCiphertext a {@link CombinedCiphertext}
 */
public record NizkStatement(
        @NonNull List<Integer> tssShareIds,
        @NonNull TssShareTable<BlsPublicKey> tssEncryptionKeys,
        @NonNull EcPolynomial polynomialCommitment,
        @NonNull CombinedCiphertext combinedCiphertext) {
    /**
     * Constructor.
     */
    public NizkStatement {
        if (Objects.requireNonNull(tssShareIds).isEmpty())
            throw new IllegalArgumentException("tssShareIds cannot be empty");
        Objects.requireNonNull(tssEncryptionKeys);
    }

    /**
     * Returns the SHA-256 hash of the information contained in this instance.
     * @return the SHA-256 hash of the information contained in this instance.
     */
    @NonNull
    public byte[] hash() {

        final HashCalculator calculator = HashUtils.getHashCalculator(HashUtils.SHA256);
        for (Integer shareIds : tssShareIds) {
            calculator.append(shareIds);
            final BlsPublicKey publicKey = tssEncryptionKeys.getForShareId(shareIds);
            calculator.append(publicKey.element().toBytes());
        }
        for (GroupElement coefficient : polynomialCommitment.coefficients()) {
            calculator.append(coefficient.toBytes());
        }
        for (GroupElement cv : combinedCiphertext.values()) {
            calculator.append(cv.toBytes());
        }
        calculator.append(combinedCiphertext.randomness().toBytes());
        return calculator.hash();
    }
}
