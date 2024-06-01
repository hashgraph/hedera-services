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

import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.SO_FAR_SO_GOOD;

import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.HandleContextImpl;
import com.hedera.node.app.workflows.handle.flow.annotations.PlatformTransactionScope;
import com.hedera.node.app.workflows.handle.flow.util.ValidationResult;
import javax.inject.Inject;

@PlatformTransactionScope
public class FeeLogic {
    private final TransactionDispatcher dispatcher;
    private final HandleContextImpl context;
    private final FailedFeeCharger failedFeeCharger;
    private final DefaultFeeCharger defaultFeeCharger;
    private final Authorizer authorizer;
    private final TransactionInfo txInfo;

    @Inject
    public FeeLogic(
            final TransactionDispatcher dispatcher,
            final HandleContextImpl context,
            final FailedFeeCharger failedFeeCharger,
            final DefaultFeeCharger defaultFeeCharger,
            final Authorizer authorizer,
            final TransactionInfo txInfo) {
        this.dispatcher = dispatcher;
        this.context = context;
        this.failedFeeCharger = failedFeeCharger;
        this.defaultFeeCharger = defaultFeeCharger;
        this.authorizer = authorizer;
        this.txInfo = txInfo;
    }

    public void chargeFees(ValidationResult validationResult, final NodeInfo creator) {
        final var hasWaivedFees = authorizer.hasWaivedFees(txInfo.payerID(), txInfo.functionality(), txInfo.txBody());
        // Calculate the fee
        final var fees = dispatcher.dispatchComputeFees(context);
        if (validationResult.status() != SO_FAR_SO_GOOD) {
            failedFeeCharger.chargeFees(validationResult, creator);
        } else {
            defaultFeeCharger.chargeFees(validationResult, creator);
        }
    }

    public void chargeNetworkFeeIfNotWaived(ValidationResult validationResult, final NodeInfo creator) {
        final var hasWaivedFees = authorizer.hasWaivedFees(txInfo.payerID(), txInfo.functionality(), txInfo.txBody());
        // Calculate the fee
        final var fees = dispatcher.dispatchComputeFees(context);
        if (!hasWaivedFees) {
            defaultFeeCharger.chargeFees(validationResult, creator);
        }
    }
}
