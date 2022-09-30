/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.grpc.marshalling;

import static com.hedera.services.grpc.marshalling.AdjustmentUtils.adjustedFractionalChange;
import static com.hedera.services.state.submerkle.FcCustomFee.FeeType.FRACTIONAL_FEE;
import static com.hedera.services.state.submerkle.FcCustomFee.fixedFee;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.hedera.services.fees.CustomFeePayerExemptions;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.state.submerkle.FractionalFeeSpec;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FractionalFeeAssessor {
    private final FixedFeeAssessor fixedFeeAssessor;
    private final CustomFeePayerExemptions customFeePayerExemptions;

    @Inject
    public FractionalFeeAssessor(
            FixedFeeAssessor fixedFeeAssessor, CustomFeePayerExemptions customFeePayerExemptions) {
        this.fixedFeeAssessor = fixedFeeAssessor;
        this.customFeePayerExemptions = customFeePayerExemptions;
    }

    @SuppressWarnings({"java:S135", "java:S3776"})
    public ResponseCodeEnum assessAllFractional(
            BalanceChange change,
            CustomFeeMeta feeMeta,
            BalanceChangeManager changeManager,
            List<FcAssessedCustomFee> accumulator) {
        if (change.isForNft()) {
            return INVALID_TOKEN_ID;
        }
        final var initialUnits = -change.getAggregatedUnits();
        if (initialUnits < 0) {
            throw new IllegalArgumentException("Cannot assess fees to a credit");
        }

        var unitsLeft = initialUnits;
        final var payer = change.getAccount();
        final var denom = change.getToken();
        final var creditsToReclaimFrom = changeManager.creditsInCurrentLevel(denom);
        /* These accounts receiving the reclaimed credits are the
        effective payers unless the net-of-transfers flag is set. */
        final var effPayerAccountNums = effPayerAccountNumsOf(creditsToReclaimFrom);
        for (var fee : feeMeta.customFees()) {
            final var collector = fee.getFeeCollectorAsId();
            // If the collector 0.0.C for a fractional fee is trying to send X units to
            // a receiver 0.0.R, then we want to let all X units go to 0.0.R, instead of
            // reclaiming some fraction of them
            if (fee.getFeeType() != FRACTIONAL_FEE || payer.equals(collector)) {
                continue;
            }
            final var filteredCredits = filteredByExemptions(creditsToReclaimFrom, feeMeta, fee);
            if (filteredCredits.isEmpty()) {
                continue;
            }

            final var spec = fee.getFractionalFeeSpec();
            var assessedAmount = 0L;
            try {
                assessedAmount = amountOwedGiven(initialUnits, spec);
            } catch (ArithmeticException ignore) {
                return CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE;
            }

            if (spec.isNetOfTransfers()) {
                final var addedFee =
                        fixedFee(
                                assessedAmount,
                                denom.asEntityId(),
                                fee.getFeeCollector(),
                                fee.getAllCollectorsAreExempt());
                fixedFeeAssessor.assess(payer, feeMeta, addedFee, changeManager, accumulator);
            } else {
                long exemptAmount;
                try {
                    exemptAmount = reclaim(assessedAmount, filteredCredits);
                } catch (ArithmeticException ignore) {
                    return CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE;
                }
                assessedAmount -= exemptAmount;
                unitsLeft -= assessedAmount;
                if (unitsLeft < 0) {
                    return INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
                }
                adjustedFractionalChange(collector, denom, assessedAmount, changeManager);
                final var finalEffPayerNums =
                        (filteredCredits == creditsToReclaimFrom)
                                ? effPayerAccountNums
                                : effPayerAccountNumsOf(filteredCredits);
                final var assessed =
                        new FcAssessedCustomFee(
                                collector.asEntityId(),
                                denom.asEntityId(),
                                assessedAmount,
                                finalEffPayerNums);
                accumulator.add(assessed);
            }
        }
        return OK;
    }

    private long[] effPayerAccountNumsOf(List<BalanceChange> credits) {
        int n = credits.size();
        final var nums = new long[n];
        for (int i = 0; i < n; i++) {
            nums[i] = credits.get(i).getAccount().num();
        }
        return nums;
    }

    long reclaim(final long amount, final List<BalanceChange> credits) {
        var availableToReclaim = 0L;
        for (var credit : credits) {
            availableToReclaim += credit.getAggregatedUnits();
            if (availableToReclaim < 0L) {
                throw new ArithmeticException();
            }
        }

        var amountReclaimed = 0L;
        for (var credit : credits) {
            var toReclaimHere =
                    AdjustmentUtils.safeFractionMultiply(
                            credit.getAggregatedUnits(), availableToReclaim, amount);
            credit.aggregateUnits(-toReclaimHere);
            amountReclaimed += toReclaimHere;
        }

        if (amountReclaimed < amount) {
            var leftToReclaim = amount - amountReclaimed;
            for (var credit : credits) {
                final var toReclaimHere = Math.min(credit.getAggregatedUnits(), leftToReclaim);
                credit.aggregateUnits(-toReclaimHere);
                amountReclaimed += toReclaimHere;
                leftToReclaim -= toReclaimHere;
                if (leftToReclaim == 0) {
                    break;
                }
            }
        }
        return amount - amountReclaimed;
    }

    private List<BalanceChange> filteredByExemptions(
            final List<BalanceChange> credits, final CustomFeeMeta feeMeta, final FcCustomFee fee) {
        List<BalanceChange> filteredCredits = null;
        for (int i = 0, n = credits.size(); i < n; i++) {
            final var credit = credits.get(i);
            if (customFeePayerExemptions.isPayerExempt(feeMeta, fee, credit.getAccount())) {
                if (filteredCredits == null) {
                    filteredCredits = new ArrayList<>(credits.subList(0, i));
                }
            } else {
                if (filteredCredits != null) {
                    filteredCredits.add(credit);
                }
            }
        }
        return filteredCredits != null ? filteredCredits : credits;
    }

    long amountOwedGiven(long initialUnits, FractionalFeeSpec spec) {
        final var nominalFee =
                AdjustmentUtils.safeFractionMultiply(
                        spec.getNumerator(), spec.getDenominator(), initialUnits);
        long effectiveFee = Math.max(nominalFee, spec.getMinimumAmount());
        if (spec.getMaximumUnitsToCollect() > 0) {
            effectiveFee = Math.min(effectiveFee, spec.getMaximumUnitsToCollect());
        }
        return effectiveFee;
    }
}
