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

import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.flow.util.ValidationResult;

public class DefaultFeeCharger implements FeeCharger {
    private final FeeAccumulator feeAccumulator;
    private final Authorizer authorizer;
    private final TransactionInfo txInfo;

    public DefaultFeeCharger(
            final FeeAccumulator feeAccumulator, final Authorizer authorizer, final TransactionInfo txInfo) {
        this.feeAccumulator = feeAccumulator;
        this.authorizer = authorizer;
        this.txInfo = txInfo;
    }

    @Override
    public void chargeFees(final ValidationResult validationResult, final NodeInfo creator) {
        final var hasWaivedFees = authorizer.hasWaivedFees(txInfo.payerID(), txInfo.functionality(), txInfo.txBody());
        if (!hasWaivedFees) {
            final var fees = validationResult.fees();
            feeAccumulator.chargeFees(creator.accountId(), fees.networkFee());
        }
    }

    @Override
    public void chargeNetworkFee(final ValidationResult validationResult, final NodeInfo creator) {
        final var hasWaivedFees = authorizer.hasWaivedFees(txInfo.payerID(), txInfo.functionality(), txInfo.txBody());
        if (!hasWaivedFees) {
            final var fees = validationResult.fees();
            feeAccumulator.chargeNetworkFee(creator.accountId(), fees.networkFee());
        }
    }
}
