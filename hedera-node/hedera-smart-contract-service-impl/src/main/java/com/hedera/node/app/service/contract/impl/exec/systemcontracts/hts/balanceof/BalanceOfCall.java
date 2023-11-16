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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract.HTS_PRECOMPILE_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.accountNumberForEvmReference;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmContractId;
import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.contractFunctionResultFailedFor;
import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.contractFunctionResultSuccessFor;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractRevertibleTokenViewCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.utils.SystemContractUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;

/**
 * Implements the token redirect {@code balanceOf()} call of the HTS system contract.
 */
public class BalanceOfCall extends AbstractRevertibleTokenViewCall {
    private final Address owner;

    public BalanceOfCall(
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final SystemContractGasCalculator gasCalculator,
            @Nullable final Token token,
            @NonNull final Address owner) {
        super(gasCalculator, enhancement, token);
        this.owner = requireNonNull(owner);
    }

    @Override
    public @NonNull PricedResult execute() {
        PricedResult result;
        long gasRequirement;
        ContractID contractID =
                asEvmContractId(org.hyperledger.besu.datatypes.Address.fromHexString(HTS_PRECOMPILE_ADDRESS));

        if (token == null) {
            result = gasOnly(revertResult(INVALID_TOKEN_ID, gasCalculator.viewGasRequirement()));

            gasRequirement = result.fullResult().gasRequirement();
            enhancement
                    .systemOperations()
                    .externalizeResult(
                            contractFunctionResultFailedFor(gasRequirement, INVALID_TOKEN_ID.toString(), contractID),
                            SystemContractUtils.ResultStatus.IS_ERROR,
                            INVALID_TOKEN_ID);
        } else {
            result = gasOnly(resultOfViewingToken(token));

            gasRequirement = result.fullResult().gasRequirement();
            final var output = result.fullResult().result().getOutput();
            enhancement
                    .systemOperations()
                    .externalizeResult(
                            contractFunctionResultSuccessFor(gasRequirement, output, contractID),
                            SystemContractUtils.ResultStatus.IS_SUCCESS,
                            SUCCESS);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected @NonNull HederaSystemContract.FullResult resultOfViewingToken(@NonNull Token token) {
        final var ownerNum = accountNumberForEvmReference(owner, nativeOperations());
        if (ownerNum < 0) {
            return revertResult(INVALID_ACCOUNT_ID, gasCalculator.viewGasRequirement());
        }

        final var tokenNum = token.tokenIdOrThrow().tokenNum();
        final var relation = nativeOperations().getTokenRelation(ownerNum, tokenNum);
        final var balance = relation == null ? 0 : relation.balance();
        final var output = BalanceOfTranslator.BALANCE_OF.getOutputs().encodeElements(BigInteger.valueOf(balance));

        return successResult(output, gasCalculator.viewGasRequirement());
    }
}
