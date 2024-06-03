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

package com.hedera.node.app.workflows.handle.flow.infra.fees;

import static com.hedera.hapi.node.base.HederaFunctionality.ETHEREUM_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.SO_FAR_SO_GOOD;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.flow.annotations.PlatformTransactionScope;
import com.hedera.node.app.workflows.handle.flow.util.ValidationResult;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import java.util.EnumSet;
import java.util.Set;
import javax.inject.Inject;

@PlatformTransactionScope
public class FeeLogic {
    private static final Set<HederaFunctionality> DISPATCHING_CONTRACT_TRANSACTIONS =
            EnumSet.of(HederaFunctionality.CONTRACT_CREATE, HederaFunctionality.CONTRACT_CALL, ETHEREUM_TRANSACTION);

    private final FailedFeeCharger failedFeeCharger;
    private final DefaultFeeCharger defaultFeeCharger;
    private final Authorizer authorizer;
    private final TransactionInfo txInfo;
    private final RecordListBuilder recordListBuilder;

    @Inject
    public FeeLogic(
            final FailedFeeCharger failedFeeCharger,
            final DefaultFeeCharger defaultFeeCharger,
            final Authorizer authorizer,
            final TransactionInfo txInfo,
            final RecordListBuilder recordListBuilder) {
        this.failedFeeCharger = failedFeeCharger;
        this.defaultFeeCharger = defaultFeeCharger;
        this.authorizer = authorizer;
        this.txInfo = txInfo;
        this.recordListBuilder = recordListBuilder;
    }

    public void chargeFees(ValidationResult validationResult, final NodeInfo creator, Fees fees) {
        final var hasWaivedFees = authorizer.hasWaivedFees(txInfo.payerID(), txInfo.functionality(), txInfo.txBody());
        if (hasWaivedFees) {
            return;
        }
        if (validationResult != null && validationResult.status() != SO_FAR_SO_GOOD) {
            failedFeeCharger.chargeFees(validationResult, creator, fees);
        } else {
            defaultFeeCharger.chargeFees(validationResult, creator, fees);
        }
    }

    public void chargeForPrecedingTxns() {
        final var functionality = txInfo.functionality();
        // Possibly charge assessed fees for preceding child transactions; but
        // only if not a contract operation, since these dispatches were already
        // charged using gas. [FUTURE - stop setting transactionFee in recordBuilder
        // at the point of dispatch, so we no longer need this special case here.]
        final var isContractOp = DISPATCHING_CONTRACT_TRANSACTIONS.contains(functionality);
        if (!isContractOp && !recordListBuilder.precedingRecordBuilders().isEmpty()) {
            // We intentionally charge fees even if the transaction failed (may need to update
            // mono-service to this behavior?)
            final var childFees = recordListBuilder.precedingRecordBuilders().stream()
                    .mapToLong(SingleTransactionRecordBuilderImpl::transactionFee)
                    .sum();
            // If the payer is authorized to waive fees, then we don't charge them
            if (chargeNetworkFeeIfNotWaived(childFees)) {
                throw new HandleException(INSUFFICIENT_PAYER_BALANCE);
            }
        }
    }

    private boolean chargeNetworkFeeIfNotWaived(long networkFee) {
        final var hasWaivedFees = authorizer.hasWaivedFees(txInfo.payerID(), txInfo.functionality(), txInfo.txBody());
        if (!hasWaivedFees) {
            return defaultFeeCharger.chargeNetworkFees(networkFee);
        }
        return true;
    }
}
