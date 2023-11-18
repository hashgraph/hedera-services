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
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater.Enhancement;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;

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
    public PricedResult execute() {
        if (token == null) {
            return reversionWith(INVALID_TOKEN_ID, gasCalculator.canonicalGasRequirement(DispatchType.APPROVE));
        }
        final var body = callGrantApproval();
        final var recordBuilder = systemContractOperations()
                .dispatch(body, verificationStrategy, senderId, SingleTransactionRecordBuilder.class);
        final var gasRequirement = gasCalculator.gasRequirement(body, DispatchType.APPROVE, senderId);
        final var status = recordBuilder.status();
        if (status != ResponseCodeEnum.SUCCESS) {
            return gasOnly(revertResult(status, gasRequirement), status, false);
        } else {
            final var encodedOutput = tokenType.equals(TokenType.FUNGIBLE_COMMON)
                    ? GrantApprovalTranslator.ERC_GRANT_APPROVAL.getOutputs().encodeElements(true)
                    : GrantApprovalTranslator.ERC_GRANT_APPROVAL_NFT
                            .getOutputs()
                            .encodeElements();
            return gasOnly(successResult(encodedOutput, gasRequirement), status, false);
        }
    }
}
