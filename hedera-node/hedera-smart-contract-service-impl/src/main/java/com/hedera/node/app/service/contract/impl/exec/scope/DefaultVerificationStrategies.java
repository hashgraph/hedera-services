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

import static com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations.MISSING_ENTITY_NUMBER;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.maybeMissingNumberOf;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;

import com.hedera.hapi.node.base.ContractID;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Address;

/**
 * Implements the default {@link VerificationStrategies} for use in signature activation tests.
 */
public class DefaultVerificationStrategies implements VerificationStrategies {
    /**
     * Returns a {@link VerificationStrategy} that will activate <b>only</b> delegatable contract id and
     * contract id keys (the latter if delegatable permissions are not required).
     *
     * <p>This is the standard strategy under the approval-based security model, where a contract gains
     * authorization for an entity only by having its id or address added to that entity's controlling
     * key structure.
     *
     * @param sender the contract whose keys are to be activated
     * @param requiresDelegatePermission whether the strategy should require a delegatable contract id key
     * @param nativeOperations the operations to use for looking up the contract's number
     * @return a {@link VerificationStrategy} that will activate only delegatable contract id and contract id keys
     */
    public VerificationStrategy activatingOnlyContractKeysFor(
            @NonNull final Address sender,
            final boolean requiresDelegatePermission,
            @NonNull final HederaNativeOperations nativeOperations) {
        final var contractNum = maybeMissingNumberOf(sender, nativeOperations);
        if (contractNum == MISSING_ENTITY_NUMBER) {
            throw new IllegalArgumentException("Cannot verify against missing contract " + sender);
        }
        return new ActiveContractVerificationStrategy(
                ContractID.newBuilder().contractNum(contractNum).build(),
                tuweniToPbjBytes(sender),
                requiresDelegatePermission,
                ActiveContractVerificationStrategy.UseTopLevelSigs.NO);
    }
}
