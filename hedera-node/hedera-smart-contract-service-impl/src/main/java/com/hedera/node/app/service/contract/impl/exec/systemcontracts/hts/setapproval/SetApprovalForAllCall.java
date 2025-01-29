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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.setapproval;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbiConstants.APPROVAL_FOR_ALL_EVENT;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.encodedRc;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.standardized;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.setapproval.SetApprovalForAllTranslator.ERC721_SET_APPROVAL_FOR_ALL;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.setapproval.SetApprovalForAllTranslator.SET_APPROVAL_FOR_ALL;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.fromHeadlongAddress;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.LogBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractCallStreamBuilder;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;

public class SetApprovalForAllCall extends AbstractCall {

    private final VerificationStrategy verificationStrategy;
    private final TransactionBody transactionBody;
    private final AccountID sender;
    private final DispatchGasCalculator dispatchGasCalculator;
    private final Address token;
    private final Address spender;
    private final boolean approved;
    private final boolean isERC;

    public SetApprovalForAllCall(
            @NonNull final HtsCallAttempt attempt,
            @NonNull final TransactionBody transactionBody,
            @NonNull final DispatchGasCalculator gasCalculator,
            final boolean isERC) {
        super(attempt.systemContractGasCalculator(), attempt.enhancement(), false);
        this.transactionBody = transactionBody;
        this.dispatchGasCalculator = gasCalculator;
        this.isERC = isERC;
        this.verificationStrategy = attempt.defaultVerificationStrategy();
        this.sender = attempt.addressIdConverter().convertSender(attempt.senderAddress());
        Tuple call;
        if (isERC) {
            call = ERC721_SET_APPROVAL_FOR_ALL.decodeCall(attempt.inputBytes());
            this.token =
                    ConversionUtils.asLongZeroAddress(attempt.redirectTokenId().tokenNum());
            this.spender = fromHeadlongAddress(call.get(0));
            this.approved = call.get(1);
        } else {
            call = SET_APPROVAL_FOR_ALL.decodeCall(attempt.inputBytes());
            this.token = fromHeadlongAddress(call.get(0));
            this.spender = fromHeadlongAddress(call.get(1));
            this.approved = call.get(2);
        }
    }

    @NonNull
    @Override
    public PricedResult execute() {
        final var recordBuilder = systemContractOperations()
                .dispatch(transactionBody, verificationStrategy, sender, ContractCallStreamBuilder.class);

        final var gasRequirement =
                dispatchGasCalculator.gasRequirement(transactionBody, gasCalculator, enhancement, sender);

        final var status = recordBuilder.status();
        if (status != ResponseCodeEnum.SUCCESS) {
            // This checks ensure mono behaviour
            if (status.equals(INVALID_ALLOWANCE_SPENDER_ID)) {
                return completionWith(INVALID_ALLOWANCE_SPENDER_ID, gasRequirement);
            }
            if (status.equals(INVALID_TOKEN_ID)) {
                return completionWith(INVALID_TOKEN_ID, gasRequirement);
            }
            return reversionWith(gasRequirement, recordBuilder);
        } else {
            return completionWith(gasRequirement, recordBuilder, encodedRc(standardized(status)));
        }
    }

    @Override
    public @NonNull PricedResult execute(final MessageFrame frame) {
        final var result = execute();

        if (result.responseCode().equals(ResponseCodeEnum.SUCCESS)) {
            frame.addLog(getLogForSetApprovalForAll(token));
        }

        return result;
    }

    private Log getLogForSetApprovalForAll(final Address logger) {
        return LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(APPROVAL_FOR_ALL_EVENT)
                .forIndexedArgument(asLongZeroAddress(sender.accountNum()))
                .forIndexedArgument(spender)
                .forDataItem(approved)
                .build();
    }
}
