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

package com.hedera.node.app.tss.cryptography.tss.extensions.elgamal;

import com.hedera.node.app.tss.cryptography.pairings.api.FieldElement;
import com.hedera.node.app.tss.cryptography.pairings.api.GroupElement;
import com.hedera.node.app.tss.cryptography.pairings.extensions.EcPolynomial;
import com.hedera.node.app.tss.cryptography.tss.api.TssShareTable;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Arrays;
import java.util.List;

/**
 * A {@link CiphertextTable} contains for each share an encrypted secret, and the randomness that was used to produce the encrypted values.
 *
 * @param sharedRandomness a shared randomness for all the messages in {@code shareCiphertexts}
 * @param shareCiphertexts a ciphertext table containing {@link CipherText} for each share
 */
public record CiphertextTable(@NonNull List<GroupElement> sharedRandomness, @NonNull CipherText[] shareCiphertexts)
        implements TssShareTable<CipherText> {

    /**
     * Combines this representation into a compressed representation still containing all the information.
     * This representation is used for Nizk proofs.
     * @param base generally 256 value (which represents all the possibly distinct values we can encrypt in a byte
     * @return the compressed representation of this {@link CiphertextTable}
     */
    @NonNull
    public CombinedCiphertext combine(@NonNull final FieldElement base) {
        final GroupElement randomness = new EcPolynomial(sharedRandomness).evaluate(base);

        final List<GroupElement> values = Arrays.stream(shareCiphertexts)
                .map(cipherText -> new EcPolynomial(cipherText.cipherText()))
                .map(poly -> poly.evaluate(base))
                .toList();
        return new CombinedCiphertext(randomness, values);
    }

    @NonNull
    @Override
    public CipherText getForShareId(final int shareId) {
        return this.shareCiphertexts[shareId - 1];
    }
}
