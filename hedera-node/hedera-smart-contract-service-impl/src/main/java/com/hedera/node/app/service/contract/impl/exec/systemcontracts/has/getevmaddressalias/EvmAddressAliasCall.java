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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.getevmaddressalias;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.getevmaddressalias.EvmAddressAliasTranslator.EVM_ADDRESS_ALIAS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.explicitFromHeadlong;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.isLongZeroAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.numberOfLongZero;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallAttempt;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Implements the  {@code evmAddressAlias()} call of the Has system contract.
 * This call will return the EVM address alias of the given long zero Hedera account number if it exists.
 */
public class EvmAddressAliasCall extends AbstractCall {
    private final Address address;

    public EvmAddressAliasCall(@NonNull final HasCallAttempt attempt, final Address address) {
        super(attempt.systemContractGasCalculator(), attempt.enhancement(), true);
        this.address = requireNonNull(address);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull PricedResult execute() {
        final var explicitAddress = explicitFromHeadlong(address);

        // If the address is not a long zero then return fail
        if (!isLongZeroAddress(explicitAddress)) {
            return gasOnly(fullResultsFor(INVALID_ACCOUNT_ID, ZERO_ADDRESS), INVALID_ACCOUNT_ID, true);
        }

        final var accountNum = numberOfLongZero(explicitAddress);
        final var account = enhancement.nativeOperations().getAccount(accountNum);
        // If the account is null or does not have an account id then return bail
        if (account == null) {
            return gasOnly(fullResultsFor(INVALID_ACCOUNT_ID, ZERO_ADDRESS), INVALID_ACCOUNT_ID, true);
        }

        // If the account does not have an evm address as an alias
        if (account.alias().length() != 20) {
            return gasOnly(fullResultsFor(INVALID_ACCOUNT_ID, ZERO_ADDRESS), INVALID_ACCOUNT_ID, true);
        }

        final var aliasAddress = asHeadlongAddress(account.alias().toByteArray());
        return gasOnly(fullResultsFor(SUCCESS, aliasAddress), SUCCESS, true);
    }

    private @NonNull FullResult fullResultsFor(
            @NonNull final ResponseCodeEnum responseCode, @NonNull final Address address) {
        return successResult(
                EVM_ADDRESS_ALIAS.getOutputs().encodeElements((long) responseCode.protoOrdinal(), address),
                gasCalculator.viewGasRequirement());
    }
}
