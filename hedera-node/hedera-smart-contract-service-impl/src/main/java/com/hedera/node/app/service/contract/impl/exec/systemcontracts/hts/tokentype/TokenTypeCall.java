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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokentype;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract.HTS_PRECOMPILE_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokentype.TokenTypeTranslator.TOKEN_TYPE;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmContractId;
import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.contractFunctionResultFailedFor;
import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.contractFunctionResultSuccessFor;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractNonRevertibleTokenViewCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.utils.SystemContractUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class TokenTypeCall extends AbstractNonRevertibleTokenViewCall {
    private final boolean isStaticCall;

    public TokenTypeCall(
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            final boolean isStaticCall,
            @Nullable final Token token) {
        super(gasCalculator, enhancement, token);
        this.isStaticCall = isStaticCall;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected @NonNull HederaSystemContract.FullResult resultOfViewingToken(@NonNull final Token token) {
        requireNonNull(token);
        return fullResultsFor(
                SUCCESS, gasCalculator.viewGasRequirement(), token.tokenType().protoOrdinal());
    }

    @Override
    protected @NonNull FullResult viewCallResultWith(
            @NonNull final ResponseCodeEnum status, final long gasRequirement) {
        return fullResultsFor(status, gasRequirement, 0);
    }

    private @NonNull FullResult fullResultsFor(
            @NonNull final ResponseCodeEnum status, final long gasRequirement, final int tokenType) {
        // @Future remove to revert #9066 after modularization is completed
        if (isStaticCall && status != SUCCESS) {
            return revertResult(status, gasRequirement);
        }
        return successResult(TOKEN_TYPE.getOutputs().encodeElements(status.protoOrdinal(), tokenType), gasRequirement);
    }

    @Override
    public @NonNull PricedResult execute() {
        PricedResult result;
        long gasRequirement;
        Bytes output;

        ContractID contractID = asEvmContractId(Address.fromHexString(HTS_PRECOMPILE_ADDRESS));
        if (token == null) {
            result = gasOnly(viewCallResultWith(INVALID_TOKEN_ID, gasCalculator.viewGasRequirement()));

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
            output = result.fullResult().result().getOutput();
            enhancement
                    .systemOperations()
                    .externalizeResult(
                            contractFunctionResultSuccessFor(gasRequirement, output, contractID),
                            SystemContractUtils.ResultStatus.IS_SUCCESS,
                            SUCCESS);
        }
        return result;
    }
}
