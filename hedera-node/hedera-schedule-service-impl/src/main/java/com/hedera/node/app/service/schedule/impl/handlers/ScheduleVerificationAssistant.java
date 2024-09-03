/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl.handlers;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import java.util.Set;

/**
 * Verification Assistant that "verifies" keys previously verified via Schedule create or sign transactions.
 * This class also observes all primitive keys that are still unverified, and potentially passes those
 * on via the side effect of adding them to the sets provided in the constructor, which must be modifiable.
 */
public class ScheduleVerificationAssistant implements VerificationAssistant {
    private final Set<Key> preValidatedKeys;
    private final Set<Key> failedPrimitiveKeys;

    /**
     * Create a new schedule verification assistant.
     *
     * @param preValidatedKeys a <strong>modifiable</strong> {@code Set<Key>} of primitive keys previously verified.
     * @param failedPrimitiveKeys an empty and <strong>modifiable</strong> {@code Set<Key>} to receive a list of
     *   primitive keys that are still unverified.
     */
    public ScheduleVerificationAssistant(final Set<Key> preValidatedKeys, Set<Key> failedPrimitiveKeys) {
        this.preValidatedKeys = preValidatedKeys;
        this.failedPrimitiveKeys = failedPrimitiveKeys;
    }

    @Override
    public boolean test(final Key key, final SignatureVerification priorVerify) {
        if (key.hasKeyList() || key.hasThresholdKey() || key.hasContractID() || key.hasDelegatableContractId()) {
            return priorVerify.passed();
        } else {
            final boolean isValid = priorVerify.passed() || preValidatedKeys.contains(key);
            if (!isValid) {
                failedPrimitiveKeys.add(key);
            } else if (priorVerify.passed()) {
                preValidatedKeys.add(key);
            }
            return isValid;
        }
    }
}
