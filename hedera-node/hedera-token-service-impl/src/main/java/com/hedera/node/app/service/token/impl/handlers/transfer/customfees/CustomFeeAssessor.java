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

import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Collections.emptyList;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CustomFeeAssessor {
    private final CustomFixedFeeAssessor fixedFeeAssessor;
    private final CustomFractionalFeeAssessor fractionalFeeAssessor;
    private final CustomRoyaltyFeeAssessor royaltyFeeAssessor;
    private int numberOfCustomFeesCharged = 0;
    private int numOfTotalBalanceChanges = 0;
    private int levelNum = 0;

    @Inject
    public CustomFeeAssessor(
            @NonNull final CustomFixedFeeAssessor fixedFeeAssessor,
            @NonNull final CustomFractionalFeeAssessor fractionalFeeAssessor,
            @NonNull final CustomRoyaltyFeeAssessor royaltyFeeAssessor,
            @NonNull final CryptoTransferTransactionBody op) {
        this.fixedFeeAssessor = fixedFeeAssessor;
        this.fractionalFeeAssessor = fractionalFeeAssessor;
        this.royaltyFeeAssessor = royaltyFeeAssessor;
        numOfTotalBalanceChanges = numAdjustmentsFromOriginalBody(op);
    }

    private int numAdjustmentsFromOriginalBody(final CryptoTransferTransactionBody op) {
        final var hbarChanges = op.transfersOrElse(TransferList.DEFAULT)
                .accountAmountsOrElse(emptyList())
                .size();
        final var tokenTransfers = op.tokenTransfersOrElse(emptyList());
        var fungibleTokenChanges = 0;
        var nftTransfers = 0;
        for (final var xfer : tokenTransfers) {
            fungibleTokenChanges += xfer.transfersOrElse(emptyList()).size();
            nftTransfers += xfer.nftTransfersOrElse(emptyList()).size();
        }
        return hbarChanges + fungibleTokenChanges + nftTransfers;
    }

    public void assess(
            final AccountID sender,
            final CustomFeeMeta feeMeta,
            final TokensConfig tokensConfig,
            final LedgerConfig ledgerConfig,
            final TransferList.Builder hbarAdjustments,
            final List<TokenTransferList.Builder> htsAdjustments) {
        // increment the level to create transaction body from all custom fees assessed from original
        // transaction
        levelNum++;
        validateTrue(levelNum <= tokensConfig.maxCustomFeeDepth(), CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH);

        // If sender for this adjustment is same as treasury for token
        // then don't charge any custom fee. Since token treasuries are exempt from custom fees
        if (feeMeta.treasuryId().equals(sender)) {
            return;
        }

        final var maxTransfersSize = ledgerConfig.xferBalanceChangesMaxLen();
        assessFixedFees(feeMeta, sender, maxTransfersSize, hbarAdjustments, htsAdjustments);

        validateFalse(numOfTotalBalanceChanges > maxTransfersSize, CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS);
    }

    private void assessFixedFees(
            @NonNull final CustomFeeMeta feeMeta,
            @NonNull final AccountID sender,
            final int maxTransfersSize,
            @NonNull final TransferList.Builder hbarAdjustments,
            final List<TokenTransferList.Builder> htsAdjustments) {
        for (final var fee : feeMeta.customFees()) {
            final var collector = fee.feeCollectorAccountId();
            if (sender.equals(collector)) {
                continue;
            }
            if (fee.fee().kind().equals(CustomFee.FeeOneOfType.FIXED_FEE)) {
                // This is a top-level fixed fee, not a fallback royalty fee
                fixedFeeAssessor.assess(sender, feeMeta, fee, false, hbarAdjustments, htsAdjustments);
                validateFalse(
                        numOfTotalBalanceChanges > maxTransfersSize, CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS);
            }
        }
    }
}
