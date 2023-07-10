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

import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.adjustHbarFees;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.adjustHtsFees;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.couldTriggerCustomFees;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeExemptions.isPayerExempt;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.hapi.node.transaction.CustomFee;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CustomFixedFeeAssessor {
    @Inject
    public CustomFixedFeeAssessor() {}

    public void assessFixedFees(
            @NonNull final CustomFeeMeta feeMeta, @NonNull final AccountID sender, final AssessmentResult result) {
        for (final var fee : feeMeta.customFees()) {
            final var tokenId = feeMeta.tokenId();
            if (fee.fee().kind().equals(CustomFee.FeeOneOfType.FIXED_FEE)) {
                final var denom = fee.fixedFeeOrThrow().denominatingTokenIdOrElse(TokenID.DEFAULT);
                if (couldTriggerCustomFees(tokenId, denom, result.getExemptDebits())) {
                    final var collector = fee.feeCollectorAccountId();
                    if (sender.equals(collector)) {
                        continue;
                    }
                    // This is a top-level fixed fee, not a fallback royalty fee
                    assessFixedFee(feeMeta, sender, fee, result);
                }
            }
        }
    }

    public void assessFixedFee(
            final CustomFeeMeta feeMeta, final AccountID sender, final CustomFee fee, final AssessmentResult result) {
        if (isPayerExempt(feeMeta, fee, sender)) {
            return;
        }
        final var fixedFeeSpec = fee.fixedFeeOrThrow();
        if (!fixedFeeSpec.hasDenominatingTokenId()) {
            assessHbarFees(sender, fee, result);
        } else {
            assessHtsFees(sender, feeMeta, fee, result);
        }
    }

    private void assessHbarFees(
            @NonNull final AccountID sender, @NonNull final CustomFee hbarFee, @NonNull final AssessmentResult result) {
        final var collector = hbarFee.feeCollectorAccountId();
        final var fixedSpec = hbarFee.fixedFee();
        final var amount = fixedSpec.amount();

        adjustHbarFees(result, sender, hbarFee);

        result.addAssessedCustomFee(AssessedCustomFee.newBuilder()
                .effectivePayerAccountId(sender)
                .amount(amount)
                .feeCollectorAccountId(collector)
                .build());
    }

    private void assessHtsFees(
            @NonNull final AccountID sender,
            @NonNull final CustomFeeMeta chargingTokenMeta,
            @NonNull final CustomFee htsFee,
            @NonNull final AssessmentResult result) {
        final var htsAdjustments = result.getHtsAdjustments();
        final var exemptDenoms = result.getExemptDebits();

        final var collector = htsFee.feeCollectorAccountIdOrThrow();
        final var fixedFeeSpec = htsFee.fixedFeeOrThrow();
        final var amount = fixedFeeSpec.amount();
        final var denominatingToken = fixedFeeSpec.denominatingTokenIdOrThrow();
        adjustHtsFees(htsAdjustments, sender, collector, chargingTokenMeta, amount, denominatingToken, exemptDenoms);

        result.addAssessedCustomFee(AssessedCustomFee.newBuilder()
                .effectivePayerAccountId(sender)
                .amount(amount)
                .feeCollectorAccountId(collector)
                .tokenId(fixedFeeSpec.denominatingTokenId())
                .build());
    }
}
