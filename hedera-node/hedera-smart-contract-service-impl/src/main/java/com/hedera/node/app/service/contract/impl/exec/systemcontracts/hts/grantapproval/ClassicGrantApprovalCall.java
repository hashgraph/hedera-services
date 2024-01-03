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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbiConstants;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.LogBuilder;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater.Enhancement;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;

public class ClassicGrantApprovalCall extends AbstractGrantApprovalCall {
    public ClassicGrantApprovalCall(
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final Enhancement enhancement,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final AccountID senderId,
            @NonNull final TokenID token,
            @NonNull final AccountID spender,
            @NonNull final BigInteger amount,
            @NonNull final TokenType tokenType) {
        super(gasCalculator, enhancement, verificationStrategy, senderId, token, spender, amount, tokenType, false);
    }

    @NonNull
    @Override
    public PricedResult execute() {
        if (token == null) {
            return reversionWith(INVALID_TOKEN_ID, gasCalculator.canonicalGasRequirement(DispatchType.APPROVE));
        }
        final var body = callGrantApproval();
        final var recordBuilder = systemContractOperations()
                .dispatch(body, verificationStrategy, senderId, SingleTransactionRecordBuilder.class);
        final var status = recordBuilder.status();
        final var gasRequirement = gasCalculator.gasRequirement(body, DispatchType.APPROVE, senderId);
        if (status != ResponseCodeEnum.SUCCESS) {
            if (status.equals(INVALID_ALLOWANCE_SPENDER_ID)) {
                return reversionWith(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, gasRequirement);
            } else {
                return reversionWith(status, gasRequirement);
            }
        } else {
            final var encodedOutput = tokenType.equals(TokenType.FUNGIBLE_COMMON)
                    ? GrantApprovalTranslator.GRANT_APPROVAL.getOutputs().encodeElements((long) status.protoOrdinal())
                    : GrantApprovalTranslator.GRANT_APPROVAL_NFT.getOutputs().encodeElements((long)
                            status.protoOrdinal());

            return gasOnly(FullResult.successResult(encodedOutput, gasRequirement), status, false);
        }
    }

    @NonNull
    @Override
    public PricedResult execute(final MessageFrame frame) {
        final var result = execute();

        if (result.fullResult().result().getState().equals(MessageFrame.State.COMPLETED_SUCCESS)) {
            final var tokenAddress = asLongZeroAddress(token.tokenNum());

            if (tokenType.equals(TokenType.FUNGIBLE_COMMON)) {
                frame.addLog(getLogForFungibleAdjustAllowance(tokenAddress));
            } else {
                frame.addLog(getLogForNftAdjustAllowance(tokenAddress));
            }
        }

        return result;
    }

    private Log getLogForFungibleAdjustAllowance(final Address logger) {
        return LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(AbiConstants.APPROVAL_EVENT)
                .forIndexedArgument(asLongZeroAddress(senderId.accountNum()))
                .forIndexedArgument(asLongZeroAddress(spender.accountNum()))
                .forDataItem(amount)
                .build();
    }

    private Log getLogForNftAdjustAllowance(final Address logger) {
        return LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(AbiConstants.APPROVAL_EVENT)
                .forIndexedArgument(asLongZeroAddress(senderId.accountNum()))
                .forIndexedArgument(asLongZeroAddress(spender.accountNum()))
                .forIndexedArgument(amount)
                .build();
    }
}
