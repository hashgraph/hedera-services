/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeExemptions.isPayerExempt;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.transaction.CustomFee;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class CustomFixedFeeAssessor {
    public CustomFixedFeeAssessor() {}

    public void assess(
            final AccountID sender,
            final CustomFeeMeta feeMeta,
            final CustomFee fee,
            final boolean isFallbackFee,
            final TransferList.Builder hbarAdjustments,
            final List<TokenTransferList.Builder> htsAdjustments) {
        if (isPayerExempt(feeMeta, fee, sender)) {
            return;
        }
        final var fixedFeeSpec = fee.fixedFeeOrThrow();
        if (!fixedFeeSpec.hasDenominatingTokenId()) {
            assessHbarFees(sender, fee, isFallbackFee, hbarAdjustments);
        } else {
            assessHtsFees(sender, feeMeta, fee, isFallbackFee, htsAdjustments);
        }
    }

    public void assessHbarFees(
            final AccountID sender,
            final CustomFee hbarFee,
            boolean isFallbackFee,
            final TransferList.Builder hbarAdjustments) {
        final var collector = hbarFee.feeCollectorAccountId();
        final var fixedSpec = hbarFee.fixedFee();
        final var amount = fixedSpec.amount();
        final var aaDebit =
                AccountAmount.newBuilder().accountID(sender).amount(-amount).build();
        final var aaCredit =
                AccountAmount.newBuilder().accountID(collector).amount(amount).build();

        // TODO : How to set includesFallbackFee for signature requirement ??
        hbarAdjustments.accountAmounts(aaCredit, aaDebit);
    }

    public void assessHtsFees(
            AccountID sender,
            CustomFeeMeta chargingTokenMeta,
            CustomFee htsFee,
            boolean isFallbackFee,
            final List<TokenTransferList.Builder> htsAdjustments) {
        final var collector = htsFee.feeCollectorAccountIdOrThrow();
        final var fixedFeeSpec = htsFee.fixedFeeOrThrow();
        final var amount = fixedFeeSpec.amount();
        final var denominatingToken = fixedFeeSpec.denominatingTokenId();

        if (amount < 0) {
            addHtsAdjustment(htsAdjustments, sender, collector, amount, denominatingToken);
            if (chargingTokenMeta.tokenId().equals(denominatingToken)) {
                // TODO : How to set exempt from custom Fees
            }
        } else {
            addHtsAdjustment(htsAdjustments, sender, collector, amount, denominatingToken);
        }
    }

    private void addHtsAdjustment(
            final List<TokenTransferList.Builder> htsAdjustments,
            final AccountID sender,
            final AccountID collector,
            final long amount,
            final TokenID denominatingToken) {
        final var tokenTransferLisBuilder = TokenTransferList.newBuilder();
        final var aaDebit =
                AccountAmount.newBuilder().accountID(sender).amount(amount).build();
        final var aaCredit =
                AccountAmount.newBuilder().accountID(collector).amount(-amount).build();
        tokenTransferLisBuilder
                .token(denominatingToken)
                .transfers(aaCredit, aaDebit)
                .build();
        htsAdjustments.add(tokenTransferLisBuilder);
    }
}
