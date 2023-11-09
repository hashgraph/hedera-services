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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract.HTS_PRECOMPILE_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmContractId;
import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.contractFunctionResultSuccessFor;

import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.utils.SystemContractUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.hyperledger.besu.datatypes.Address;

/**
 * Implementation support for view calls that require an extant token.
 * ERC view function calls are generally revertible.
 */
public abstract class AbstractRevertibleTokenViewCall extends AbstractHtsCall {
    @Nullable
    private final Token token;

    protected AbstractRevertibleTokenViewCall(
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @Nullable final Token token) {
        super(gasCalculator, enhancement);
        this.token = token;
    }

    @Override
    public @NonNull PricedResult execute() {
        PricedResult result;
        if (token == null) {
            result = gasOnly(revertResult(INVALID_TOKEN_ID, gasCalculator.viewGasRequirement()));
        } else {
            result = gasOnly(resultOfViewingToken(token));
        }

        final var gasRequirement = result.fullResult().gasRequirement();
        final var output = result.fullResult().result().getOutput();
        final var contractID = asEvmContractId(Address.fromHexString(HTS_PRECOMPILE_ADDRESS));
        enhancement
                .systemOperations()
                .externalizeResult(
                        contractFunctionResultSuccessFor(gasRequirement, output, contractID),
                        SystemContractUtils.ResultStatus.IS_SUCCESS,
                        SUCCESS);

        return result;
    }

    /**
     * Returns the result of viewing the given {@code token}.
     *
     * @param token the token to view
     * @return the result of viewing the given {@code token}
     */
    @NonNull
    protected abstract HederaSystemContract.FullResult resultOfViewingToken(@NonNull Token token);
}
