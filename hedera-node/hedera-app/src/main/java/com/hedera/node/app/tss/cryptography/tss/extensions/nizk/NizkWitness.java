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

import static com.hedera.node.app.tss.cryptography.tss.extensions.elgamal.ElGamalUtils.combineFieldRandomness;

import com.hedera.node.app.tss.cryptography.pairings.api.FieldElement;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;

/**
 * Represents the private information in a Nizk proof.
 * We will prove that we know the secrets and then randomness used to encrypt the values.
 * @param randomness the randomness used for ElGamal encryption.
 * @param secrets the secrets being sharad
 */
public record NizkWitness(@NonNull FieldElement randomness, @NonNull List<FieldElement> secrets) {
    /**
     * Constructor
     */
    public NizkWitness {
        Objects.requireNonNull(randomness, "randomness must not be null");
        Objects.requireNonNull(randomness, "secrets must not be null");
    }

    /**
     * Creates a {@link NizkWitness}
     * @param randomness the randomness used to encrypt the values
     * @param secrets being shared
     * @return a {@link NizkWitness} for a combined randomness and the list of secrets
     */
    @NonNull
    public static NizkWitness create(
            @NonNull final List<FieldElement> randomness, @NonNull final List<FieldElement> secrets) {
        return new NizkWitness(combineFieldRandomness(randomness), secrets);
    }
}
