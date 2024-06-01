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

package com.hedera.node.app.workflows.handle.flow.fees;

import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.NODE_DUE_DILIGENCE_FAILURE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.PAYER_UNWILLING_OR_UNABLE_TO_PAY_SERVICE_FEE;

import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.flow.util.ValidationResult;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class FailedFeeCharger implements FeeCharger {
    private static final Logger logger = LogManager.getLogger(FailedFeeCharger.class);

    private final FeeAccumulator feeAccumulator;
    private final Authorizer authorizer;
    private final RecordListBuilder recordListBuilder;
    private final TransactionInfo txInfo;

    @Inject
    public FailedFeeCharger(
            final FeeAccumulator feeAccumulator,
            final Authorizer authorizer,
            final RecordListBuilder recordBuilder,
            final TransactionInfo txInfo) {
        this.feeAccumulator = feeAccumulator;
        this.authorizer = authorizer;
        this.recordListBuilder = recordBuilder;
        this.txInfo = txInfo;
    }

    @Override
    public void chargeFees(@NonNull final ValidationResult validationResult, @NonNull final NodeInfo creator) {
        final var payer = txInfo.payerID();
        final var fees = validationResult.fees();

        final var hasWaivedFees = authorizer.hasWaivedFees(txInfo.payerID(), txInfo.functionality(), txInfo.txBody());
        final var recordBuilder = recordListBuilder.userTransactionRecordBuilder();
        recordBuilder.status(validationResult.responseCodeEnum());
        try {
            // If the payer is authorized to waive fees, then we don't charge them
            if (!hasWaivedFees) {
                if (validationResult.status() == NODE_DUE_DILIGENCE_FAILURE) {
                    feeAccumulator.chargeNetworkFee(creator.accountId(), fees.networkFee());
                } else if (validationResult.status() == PAYER_UNWILLING_OR_UNABLE_TO_PAY_SERVICE_FEE) {
                    // We do not charge partial service fees; if the payer is unwilling or unable to cover
                    // the entire service fee, then we only charge network and node fees (prioritizing
                    // the network fee in case of a very low payer balance)
                    feeAccumulator.chargeFees(payer, creator.accountId(), fees.withoutServiceComponent());
                } else {
                    final var feesToCharge = validationResult.responseCodeEnum().equals(DUPLICATE_TRANSACTION)
                            ? fees.withoutServiceComponent()
                            : fees;
                    feeAccumulator.chargeFees(payer, creator.accountId(), feesToCharge);
                }
            }
        } catch (final HandleException ex) {
            final var identifier = validationResult.status() == NODE_DUE_DILIGENCE_FAILURE
                    ? "node " + creator.nodeId()
                    : "account " + payer;
            logger.error(
                    "Unable to charge {} a penalty after {} happened. Cause of the failed charge:",
                    identifier,
                    validationResult.responseCodeEnum(),
                    ex);
        }
    }
}
