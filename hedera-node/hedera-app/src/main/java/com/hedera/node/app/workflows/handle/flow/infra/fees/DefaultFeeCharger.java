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

import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.flow.annotations.UserTransactionScope;
import com.hedera.node.app.workflows.handle.flow.util.ValidationResult;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@UserTransactionScope
public class DefaultFeeCharger implements FeeCharger {
    private static final Logger logger = LogManager.getLogger(DefaultFeeCharger.class);

    private final FeeAccumulator feeAccumulator;
    private final TransactionInfo txInfo;

    @Inject
    public DefaultFeeCharger(final FeeAccumulator feeAccumulator, final TransactionInfo txInfo) {
        this.feeAccumulator = feeAccumulator;
        this.txInfo = txInfo;
    }

    @Override
    public void chargeFees(final ValidationResult validationResult, final NodeInfo creator, final Fees fees) {
        final var payer = txInfo.payerID();
        try {
            feeAccumulator.chargeFees(payer, creator.accountId(), fees);
        } catch (final Exception chargeException) {
            logger.error(
                    "Unable to charge account {} a penalty after an unexpected exception {}. Cause of the failed charge:",
                    payer,
                    chargeException);
        }
    }

    @Override
    public boolean chargeNetworkFees(final long networkFee) {
        return feeAccumulator.chargeNetworkFee(txInfo.payerID(), networkFee);
    }
}
