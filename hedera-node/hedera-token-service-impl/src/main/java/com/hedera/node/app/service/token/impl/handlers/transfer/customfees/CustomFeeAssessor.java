/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers.transfer.customfees;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;

@Singleton
public class CustomFeeAssessor {
    private final CustomFixedFeeAssessor fixedFeeAssessor;
    private final CustomFractionalFeeAssessor fractionalFeeAssessor;
    private final CustomRoyaltyFeeAssessor royaltyFeeAssessor;
    private int numberOfCustomFeesCharged = 0;
    private int numOfTotalBalanceChanges = 0;

    @Inject
    public CustomFeeAssessor(@NonNull final CustomFixedFeeAssessor fixedFeeAssessor,
                             @NonNull final CustomFractionalFeeAssessor fractionalFeeAssessor,
                             @NonNull final CustomRoyaltyFeeAssessor royaltyFeeAssessor) {
        this.fixedFeeAssessor = fixedFeeAssessor;
        this.fractionalFeeAssessor = fractionalFeeAssessor;
        this.royaltyFeeAssessor = royaltyFeeAssessor;
    }

    /**
     * Assess all custom fees for the given token transfer. This method is called recursively
     * for each token transfer in the transaction.
     * @param tokensConfig the tokens configuration
     * @param ledgerConfig the ledger configuration
     * @param chargingToken the token for which custom fees are being assessed
     * @param payer peyer is the sender for one adjustment in token transfer list
     */
    public void assess(final TokensConfig tokensConfig,
                       final LedgerConfig ledgerConfig,
                       final Token chargingToken,
                       final AccountID payer) {
        final var customFees = chargingToken.customFeesOrElse(Collections.emptyList());
        final var treasuryId = chargingToken.treasuryAccountId();
        /* Token treasuries are exempt from all custom fees */
        if (customFees.isEmpty() || treasuryId.equals(payer)) {
            return;
        }

        final var maxTransfersSize = ledgerConfig.xferBalanceChangesMaxLen();
        assessFixedFees(customFees, treasuryId, payer, maxTransfersSize);

        validateFalse(numOfTotalBalanceChanges > maxTransfersSize, CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS);
    }

    private TransactionBody.Builder assessFixedFees(
            List<CustomFee> customFees,
            AccountID treasuryId,
            AccountID payer,
            int maxTransfersSize) {
        for (var fee : customFees) {
            final var collector = fee.feeCollectorAccountId();
            if (payer.equals(collector)) {
                continue;
            }
            if (fee.fee().kind().equals(CustomFee.FeeOneOfType.FIXED_FEE)) {
                // This is a top-level fixed fee, not a fallback royalty fee
                fixedFeeAssessor.assess(customFees, treasuryId, payer, fee, IS_NOT_FALLBACK_FEE);
                validateFalse(numOfTotalBalanceChanges > maxTransfersSize, CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS);
            }
        }
    }
}
