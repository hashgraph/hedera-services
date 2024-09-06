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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.isvalidalias;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations.NON_CANONICAL_REFERENCE_NUMBER;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.isvalidalias.IsValidAliasTranslator.IS_VALID_ALIAS;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.accountNumberForEvmReference;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallAttempt;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Implements the {@code isValidAlias(address)} call of the HAS system contract.
 * This call will return true if the address exists and false otherwise.
 */
public class IsValidAliasCall extends AbstractCall {

    private final Address address;

    public IsValidAliasCall(@NonNull final HasCallAttempt attempt, @NonNull final Address address) {
        super(attempt.systemContractGasCalculator(), attempt.enhancement(), true);
        this.address = requireNonNull(address);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull PricedResult execute() {

        // It is OK if given address is long zero whether it does or doesn't have an EVM alias (which is
        // why NON_CANONICAL_REFERENCE_NUMBER is an acceptable result).
        final long accountNum = accountNumberForEvmReference(address, nativeOperations());
        boolean isValidAlias = accountNum >= 0 || accountNum == NON_CANONICAL_REFERENCE_NUMBER;
        return gasOnly(fullResultsFor(isValidAlias), SUCCESS, true);
    }

    private @NonNull FullResult fullResultsFor(final boolean result) {
        return successResult(IS_VALID_ALIAS.getOutputs().encodeElements(result), gasCalculator.viewGasRequirement());
    }
}
