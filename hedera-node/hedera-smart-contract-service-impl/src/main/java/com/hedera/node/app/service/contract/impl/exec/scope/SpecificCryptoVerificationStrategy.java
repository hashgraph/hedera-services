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

package com.hedera.node.app.service.contract.impl.exec.scope;

import static com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy.Decision.DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION;
import static com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy.Decision.INVALID;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@link VerificationStrategy} that will delegate verification of a specific key's signature
 * to the top-level cryptographic verification strategy; and mark all other keys invalid.
 */
public class SpecificCryptoVerificationStrategy implements VerificationStrategy {
    private final Key qualifyingKey;

    public SpecificCryptoVerificationStrategy(@NonNull final Key qualifyingKey) {
        requireNonNull(qualifyingKey);
        if (!qualifyingKey.hasEd25519() && !qualifyingKey.hasEcdsaSecp256k1()) {
            throw new IllegalArgumentException("Qualifying key must be a cryptographic key");
        }
        this.qualifyingKey = qualifyingKey;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@link VerificationStrategy.Decision#DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION} if the key
     * matches the qualifying key; {@link VerificationStrategy.Decision#INVALID} otherwise.
     *
     * @param key the key whose signature is to be verified
     * @return the decision of the strategy
     */
    @Override
    public Decision decideForPrimitive(@NonNull final Key key) {
        requireNonNull(key);
        return qualifyingKey.equals(key) ? DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION : INVALID;
    }
}
