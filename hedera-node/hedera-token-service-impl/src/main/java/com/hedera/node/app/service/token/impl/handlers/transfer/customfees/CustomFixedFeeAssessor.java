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

import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.addOrMergeHtsDebit;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeExemptions.isPayerExempt;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.CustomFee;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;

@Singleton
public class CustomFixedFeeAssessor {
    public CustomFixedFeeAssessor() {}

    public void assessFixedFees(
            @NonNull final CustomFeeMeta feeMeta,
            @NonNull final AccountID sender,
            final Map<AccountID, Long> hbarAdjustments,
            final Map<TokenID, Map<AccountID, Long>> htsAdjustments,
            final Set<TokenID> exemptDebits) {
        for (final var fee : feeMeta.customFees()) {
            final var collector = fee.feeCollectorAccountId();
            if (sender.equals(collector)) {
                continue;
            }
            if (fee.fee().kind().equals(CustomFee.FeeOneOfType.FIXED_FEE)) {
                // This is a top-level fixed fee, not a fallback royalty fee
                assessFixedFee(feeMeta, sender, fee, hbarAdjustments, htsAdjustments, exemptDebits);
            }
        }
    }

    public void assessFixedFee(
            final CustomFeeMeta feeMeta,
            final AccountID sender,
            final CustomFee fee,
            final Map<AccountID, Long> hbarAdjustments,
            final Map<TokenID, Map<AccountID, Long>> htsAdjustments,
            final Set<TokenID> exemptDebits) {
        if (isPayerExempt(feeMeta, fee, sender)) {
            return;
        }
        final var fixedFeeSpec = fee.fixedFeeOrThrow();
        if (!fixedFeeSpec.hasDenominatingTokenId()) {
            assessHbarFees(sender, fee, hbarAdjustments);
        } else {
            assessHtsFees(sender, feeMeta, fee, htsAdjustments, exemptDebits);
        }
    }

    private void assessHbarFees(
            final AccountID sender, final CustomFee hbarFee, final Map<AccountID, Long> hbarAdjustments) {
        final var collector = hbarFee.feeCollectorAccountId();
        final var fixedSpec = hbarFee.fixedFee();
        final var amount = fixedSpec.amount();
        hbarAdjustments.merge(sender, -amount, Long::sum);
        hbarAdjustments.merge(collector, amount, Long::sum);
    }

    private void assessHtsFees(
            AccountID sender,
            CustomFeeMeta chargingTokenMeta,
            CustomFee htsFee,
            final Map<TokenID, Map<AccountID, Long>> htsAdjustments,
            final Set<TokenID> exemptDenoms) {
        final var collector = htsFee.feeCollectorAccountIdOrThrow();
        final var fixedFeeSpec = htsFee.fixedFeeOrThrow();
        final var amount = fixedFeeSpec.amount();
        final var denominatingToken = fixedFeeSpec.denominatingTokenId();
        addOrMergeHtsDebit(
                htsAdjustments, sender, collector, chargingTokenMeta, amount, denominatingToken, exemptDenoms);
    }
}
