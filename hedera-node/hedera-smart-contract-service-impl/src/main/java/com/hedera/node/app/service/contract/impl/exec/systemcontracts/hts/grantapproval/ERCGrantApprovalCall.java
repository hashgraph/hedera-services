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
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract.HTS_EVM_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmContractId;
import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.contractFunctionResultFailedForProto;
import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.contractFunctionResultSuccessFor;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater.Enhancement;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class ERCGrantApprovalCall extends AbstractGrantApprovalCall {

    public ERCGrantApprovalCall(
            @NonNull final Enhancement enhancement,
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final AccountID sender,
            @NonNull final TokenID token,
            @NonNull final AccountID spender,
            @NonNull final BigInteger amount,
            @NonNull final TokenType tokenType) {
        super(gasCalculator, enhancement, verificationStrategy, sender, token, spender, amount, tokenType, false);
    }

    @NonNull
    @Override
    public PricedResult execute(MessageFrame frame) {
        if (token == null) {
            return reversionWith(INVALID_TOKEN_ID, gasCalculator.canonicalGasRequirement(DispatchType.APPROVE));
        }
        final var spenderAccount = enhancement.nativeOperations().getAccount(spender.accountNum());
        final var body = callGrantApproval();
        // validate NFT approval call
        if (tokenType.equals(TokenType.NON_FUNGIBLE_UNIQUE)) {
            // check for INVALID_TOKEN_NFT_SERIAL_NUMBER
            final var nft = nativeOperations().getNft(token.tokenNum(), amount.longValue());
            if (nft == null) {
                return externalizeAndRevert(INVALID_TOKEN_NFT_SERIAL_NUMBER, body, frame);
            }
            // check for INVALID_ALLOWANCE_SPENDER_ID
            if (spenderAccount == null && spender.accountNum() != 0) {
                return externalizeAndRevert(INVALID_ALLOWANCE_SPENDER_ID, body, frame);
            }
            // check for SENDER_DOES_NOT_OWN_NFT_SERIAL_NO
            if (!senderId.equals(getOwnerId()) && !senderHasAllowance()) {
                return externalizeAndRevert(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO, body, frame);
            }
        }

        final var recordBuilder = systemContractOperations()
                .dispatch(body, verificationStrategy, senderId, ContractCallRecordBuilder.class);
        final var gasRequirement = gasCalculator.gasRequirement(body, DispatchType.APPROVE, senderId);
        final var status = recordBuilder.status();

        // log approve
        if (tokenType.equals(TokenType.NON_FUNGIBLE_UNIQUE)) {
            GrantApprovalLoggingUtils.logSuccessfulNFTApprove(
                    token, senderId, spender, amount.longValue(), readableAccountStore(), frame);
        } else {
            GrantApprovalLoggingUtils.logSuccessfulFTApprove(
                    token, senderId, spender, amount.longValue(), readableAccountStore(), frame);
        }

        if (status != ResponseCodeEnum.SUCCESS) {
            return gasOnly(revertResult(status, gasRequirement), status, false);
        } else {
            //            final var encodedOutput = tokenType.equals(TokenType.NON_FUNGIBLE_UNIQUE)
            //                    ? GrantApprovalTranslator.ERC_GRANT_APPROVAL.getOutputs().encodeElements(true)
            //                    : GrantApprovalTranslator.ERC_GRANT_APPROVAL_NFT
            //                            .getOutputs()
            //                            .encodeElements();

            // todo check why in mono nft approve call has output!
            final var encodedOutput =
                    GrantApprovalTranslator.ERC_GRANT_APPROVAL.getOutputs().encodeElements(true);

            return gasOnly(successResult(encodedOutput, gasRequirement, recordBuilder), status, false);
        }
    }

    private PricedResult externalizeAndRevert(ResponseCodeEnum response, TransactionBody body, MessageFrame frame) {
        var gasRequirement = gasCalculator.canonicalGasRequirement(DispatchType.APPROVE);
        var revertResult = FullResult.revertResult(response, gasRequirement);
        var result = gasOnly(revertResult, response, false);

        var contractID = asEvmContractId(Address.fromHexString(HTS_EVM_ADDRESS));
        var encodedRc = ReturnTypes.encodedRc(response).array();

        // match mono record structure
        if (response.equals(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO)) {
            var contractFunctionResult = contractFunctionResultSuccessFor(
                    gasRequirement,
                    org.apache.tuweni.bytes.Bytes.wrap(encodedRc),
                    frame.getRemainingGas(),
                    frame.getInputData(),
                    senderId);
            contractFunctionResult = contractFunctionResult
                    .copyBuilder()
                    .errorMessage(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO.protoName())
                    .build();
            final var bodyBytes = TransactionBody.PROTOBUF.toBytes(body);
            final var signedTransaction =
                    SignedTransaction.newBuilder().bodyBytes(bodyBytes).build();
            final var signedTransactionBytes = SignedTransaction.PROTOBUF.toBytes(signedTransaction);
            final var transaction = Transaction.newBuilder()
                    .signedTransactionBytes(signedTransactionBytes)
                    .build();

            enhancement
                    .systemOperations()
                    .externalizeResult(contractFunctionResult, SENDER_DOES_NOT_OWN_NFT_SERIAL_NO, transaction);
        } else {
            var contractFunctionResult = contractFunctionResultFailedForProto(
                    gasRequirement, response.protoName(), contractID, Bytes.wrap(encodedRc));
            enhancement.systemOperations().externalizeResult(contractFunctionResult, response);
        }
        return result;
    }
}
