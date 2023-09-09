/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations.MISSING_ENTITY_NUMBER;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.maybeMissingNumberOf;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.hyperledger.besu.datatypes.Address;

@Singleton
public class VerificationStrategies {
    @Inject
    public VerificationStrategies() {
        // Dagger2
    }

    public VerificationStrategy onlyActivatingContractKeys(
            @NonNull final Address sender,
            @NonNull final HederaNativeOperations nativeOperations,
            final boolean requiresDelegatePermission) {
        final var contractNum = maybeMissingNumberOf(sender, nativeOperations);
        if (contractNum == MISSING_ENTITY_NUMBER) {
            throw new IllegalArgumentException("Cannot verify against missing contract " + sender);
        }
        return new ActiveContractVerificationStrategy(
                contractNum, tuweniToPbjBytes(sender), requiresDelegatePermission);
    }
}
