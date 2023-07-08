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

import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.asFixedFee;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.safeFractionMultiply;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeExemptions.isPayerExempt;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.FractionalFee;
import com.hedera.node.app.spi.workflows.HandleException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CustomFractionalFeeAssessor {
    private final CustomFixedFeeAssessor fixedFeeAssessor;

    @Inject
    public CustomFractionalFeeAssessor(CustomFixedFeeAssessor fixedFeeAssessor) {
        this.fixedFeeAssessor = fixedFeeAssessor;
    }

    public void assessFractionFees(
            final CustomFeeMeta feeMeta,
            final AccountID sender,
            final Map<TokenID, Map<AccountID, Long>> inputTokenTransfers,
            final Map<AccountID, Long> hbarAdjustments,
            final Map<TokenID, Map<AccountID, Long>> htsAdjustments,
            final Set<TokenID> exemptDebits) {
        final var tokenId = feeMeta.tokenId();
        var unitsLeft = -inputTokenTransfers.get(tokenId).get(sender);
        final var creditsForToken = getCreditsForToken(inputTokenTransfers.get(tokenId));
        for (final var fee : feeMeta.customFees()) {
            final var collector = fee.feeCollectorAccountId();
            // If the collector 0.0.C for a fractional fee is trying to send X units to
            // a receiver 0.0.R, then we want to let all X units go to 0.0.R, instead of
            // reclaiming some fraction of them
            if (fee.fee().kind().equals(CustomFee.FeeOneOfType.FRACTIONAL_FEE) || sender.equals(collector)) {
                continue;
            }
            final var fractionalFee = fee.fractionalFee();
            final var filteredCredits = filteredByExemptions(creditsForToken, feeMeta, fee);
            if (filteredCredits.isEmpty()) {
                continue;
            }
            var assessedAmount = 0L;
            try {
                assessedAmount = amountOwedGiven(unitsLeft, fractionalFee);
            } catch (ArithmeticException ignore) {
                throw new HandleException(CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE);
            }

            if (fractionalFee.netOfTransfers()) {
                final var addedFee =
                        asFixedFee(assessedAmount, tokenId, fee.feeCollectorAccountId(), fee.allCollectorsAreExempt());
                fixedFeeAssessor.assessFixedFee(
                        feeMeta, sender, addedFee, hbarAdjustments, htsAdjustments, exemptDebits);
            } else {
                long exemptAmount = reclaim(assessedAmount, filteredCredits);

                assessedAmount -= exemptAmount;
                unitsLeft -= assessedAmount;
                validateTrue(unitsLeft >= 0, INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE);
                final var map = htsAdjustments.get(tokenId);
                map.merge(collector, assessedAmount, Long::sum);
                htsAdjustments.put(tokenId, map);
            }
        }
    }

    private Map<AccountID, Long> filteredByExemptions(
            final Map<AccountID, Long> creditsForToken, final CustomFeeMeta feeMeta, final CustomFee fee) {
        final var filteredCredits = new HashMap<AccountID, Long>();
        for (final var entry : creditsForToken.entrySet()) {
            final var account = entry.getKey();
            final var amount = entry.getValue();
            if (!isPayerExempt(feeMeta, fee, account)) {
                filteredCredits.put(account, amount);
            }
        }
        return !filteredCredits.isEmpty() ? filteredCredits : creditsForToken;
    }

    private Map<AccountID, Long> getCreditsForToken(final Map<AccountID, Long> tokenIdChanges) {
        final var credits = new HashMap<AccountID, Long>();
        for (final var entry : tokenIdChanges.entrySet()) {
            final var account = entry.getKey();
            final var amount = entry.getValue();
            if (amount > 0) {
                credits.put(account, amount);
            }
        }
        return credits;
    }

    private long amountOwedGiven(long initialUnits, FractionalFee fractionalFee) {
        final var numerator = fractionalFee.fractionalAmount().numerator();
        final var denominator = fractionalFee.fractionalAmount().denominator();

        final var nominalFee = safeFractionMultiply(numerator, denominator, initialUnits);
        long effectiveFee = Math.max(nominalFee, fractionalFee.minimumAmount());
        if (fractionalFee.maximumAmount() > 0) {
            effectiveFee = Math.min(effectiveFee, fractionalFee.maximumAmount());
        }
        return effectiveFee;
    }

    private long reclaim(final long amount, final Map<AccountID, Long> credits) {
        var availableToReclaim = 0L;
        for (final var entry : credits.entrySet()) {
            availableToReclaim += entry.getValue();
            if (availableToReclaim < 0L) {
                throw new HandleException(CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE);
            }
        }

        var amountReclaimed = 0L;
        for (final var entry : credits.entrySet()) {
            final var account = entry.getKey();
            final var creditAmount = entry.getValue();
            final var toReclaimHere = safeFractionMultiply(creditAmount, availableToReclaim, amount);
            credits.put(account, creditAmount - toReclaimHere);
            amountReclaimed += toReclaimHere;
        }

        if (amountReclaimed < amount) {
            var leftToReclaim = amount - amountReclaimed;
            for (final var entry : credits.entrySet()) {
                final var account = entry.getKey();
                final var creditAmount = entry.getValue();
                final var toReclaimHere = Math.min(creditAmount, leftToReclaim);
                credits.put(account, creditAmount - toReclaimHere);
                amountReclaimed += toReclaimHere;
                leftToReclaim -= toReclaimHere;
                if (leftToReclaim == 0) {
                    break;
                }
            }
        }
        return amount - amountReclaimed;
    }
}
