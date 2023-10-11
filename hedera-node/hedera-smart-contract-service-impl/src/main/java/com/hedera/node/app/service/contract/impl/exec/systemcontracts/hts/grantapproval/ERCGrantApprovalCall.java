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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.CryptoApproveAllowanceTransactionBody;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.hapi.node.token.TokenAllowance;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater.Enhancement;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;

public class ERCGrantApprovalCall extends AbstractHtsCall {

    private final VerificationStrategy verificationStrategy;
    private final AccountID sender;
    private final TokenID token;
    private final AccountID spender;
    private final BigInteger amount;
    private final TokenType tokenType;

    public ERCGrantApprovalCall(
            @NonNull final Enhancement enhancement,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final AccountID sender,
            @NonNull final TokenID token,
            @NonNull final AccountID spender,
            @NonNull final BigInteger amount,
            @NonNull final TokenType tokenType) {
        super(enhancement);
        this.verificationStrategy = verificationStrategy;
        this.sender = sender;
        this.token = token;
        this.spender = spender;
        this.amount = amount;
        this.tokenType = tokenType;
    }

    public TransactionBody callERCGrantApproval() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(sender).build())
                .cryptoApproveAllowance(approve(token, spender, amount, tokenType))
                .build();
    }

    private CryptoApproveAllowanceTransactionBody approve(
            @NonNull final TokenID token,
            @NonNull final AccountID spender,
            @NonNull final BigInteger amount,
            @NonNull final TokenType tokenType) {
        return tokenType.equals(TokenType.FUNGIBLE_COMMON)
                ? CryptoApproveAllowanceTransactionBody.newBuilder()
                        .tokenAllowances(TokenAllowance.newBuilder()
                                .tokenId(token)
                                .spender(spender)
                                .amount(amount.longValue())
                                .build())
                        .build()
                : CryptoApproveAllowanceTransactionBody.newBuilder()
                        .nftAllowances(NftAllowance.newBuilder()
                                .tokenId(token)
                                .spender(spender)
                                .serialNumbers(amount.longValue())
                                .build())
                        .build();
    }

    @NonNull
    @Override
    public PricedResult execute() {
        // TODO - gas calculation
        if (token == null) {
            return reversionWith(INVALID_TOKEN_ID, 0L);
        }
        final var recordBuilder = systemContractOperations()
                .dispatch(callERCGrantApproval(), verificationStrategy, sender, SingleTransactionRecordBuilder.class);
        if (recordBuilder.status() != ResponseCodeEnum.SUCCESS) {
            return gasOnly(revertResult(recordBuilder.status(), 0L));
        } else {
            final var encodedOutput = tokenType.equals(TokenType.FUNGIBLE_COMMON)
                    ? GrantApprovalTranslator.ERC_GRANT_APPROVAL.getOutputs().encodeElements(true)
                    : GrantApprovalTranslator.ERC_GRANT_APPROVAL_NFT
                            .getOutputs()
                            .encodeElements();
            return gasOnly(successResult(encodedOutput, 0L));
        }
    }
}
