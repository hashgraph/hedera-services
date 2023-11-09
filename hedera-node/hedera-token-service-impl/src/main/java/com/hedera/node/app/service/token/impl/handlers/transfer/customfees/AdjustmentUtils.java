/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.FixedFee;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class AdjustmentUtils {
    public static final Function<TokenID, Map<AccountID, Long>> ADJUSTMENTS_MAP_FACTORY = ignore -> new HashMap<>();

    private AdjustmentUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * This method is used to adjust the balance changes for a custom fee. It is used for
     * fractional fees to calculate the amount to be charged.
     * @param numerator The numerator of the fraction
     * @param denominator The denominator of the fraction
     * @param amount The given units transferred
     * @return The amount to be charged
     */
    public static long safeFractionMultiply(final long numerator, final long denominator, final long amount) {
        if (amount != 0 && numerator > Long.MAX_VALUE / amount) {
            return BigInteger.valueOf(amount)
                    .multiply(BigInteger.valueOf(numerator))
                    .divide(BigInteger.valueOf(denominator))
                    .longValueExact();
        } else {
            return numerator * amount / denominator;
        }
    }

    /**
     * Given the token deomination, fee collector and the amount to be charged, this method
     * returns the {@link FixedFee} representation of custom fee.
     * @param unitsToCollect The amount to be charged
     * @param tokenDenomination The token denomination
     * @param feeCollector The fee collector
     * @param allCollectorsAreExempt Whether all collectors are exempt
     * @return The {@link FixedFee} representation of custom fee
     */
    public static CustomFee asFixedFee(
            final long unitsToCollect,
            final TokenID tokenDenomination,
            final AccountID feeCollector,
            final boolean allCollectorsAreExempt) {
        Objects.requireNonNull(feeCollector);
        final var spec = FixedFee.newBuilder()
                .denominatingTokenId(tokenDenomination)
                .amount(unitsToCollect)
                .build();
        return CustomFee.newBuilder()
                .fixedFee(spec)
                .feeCollectorAccountId(feeCollector)
                .allCollectorsAreExempt(allCollectorsAreExempt)
                .build();
    }

    /**
     * Adjusts a HTS fee. If the fee is self-denominated, it should not trigger custom fees again
     * So add the adjustment to previous level transaction. If the fee is not self-denominated,
     * it should trigger custom fees again. So add the adjustment to next level transaction.
     * @param result The {@link AssessmentResult} object
     * @param sender The sender account
     * @param collector The fee collector
     * @param chargingTokenMeta The {@link CustomFeeMeta} object of token to be charged
     * @param amount The amount to be charged
     * @param denominatingToken The token denomination
     */
    public static void adjustHtsFees(
            final AssessmentResult result,
            final AccountID sender,
            final AccountID collector,
            final CustomFeeMeta chargingTokenMeta,
            final long amount,
            final TokenID denominatingToken) {
        final var newHtsAdjustments = result.getHtsAdjustments();
        final var inputHtsAdjustments = result.getMutableInputTokenAdjustments();

        // If the fee is self-denominated, we don't need it to trigger next level custom fees
        // So add assessments in given input transaction body.
        if (chargingTokenMeta.tokenId().equals(denominatingToken)) {
            // If the fee is self-denominated, it should not trigger custom fees again
            // So add the adjustment to previous level transaction
            addHtsAdjustment(inputHtsAdjustments, sender, collector, amount, denominatingToken);
        } else {
            // Any change that might trigger next level custom fees should be added to next
            // level transaction adjustments
            addHtsAdjustment(newHtsAdjustments, sender, collector, amount, denominatingToken);
        }
    }

    /**
     * Adds HTS adjustment to given adjustments map. It makes 2 adjustments a debit
     * for sender and credit to collector
     * If there is already an entry merges the new change with it, otherwise creates a new entry.
     * @param htsAdjustments given adjustments map
     * @param sender sender account
     * @param collector collector account
     * @param amount amount to be charged
     * @param denominatingToken token denomination
     */
    private static void addHtsAdjustment(
            final Map<TokenID, Map<AccountID, Long>> htsAdjustments,
            final AccountID sender,
            final AccountID collector,
            final long amount,
            final TokenID denominatingToken) {
        final var denominatingTokenMap = htsAdjustments.computeIfAbsent(denominatingToken, ADJUSTMENTS_MAP_FACTORY);
        denominatingTokenMap.merge(sender, -amount, Long::sum);
        denominatingTokenMap.merge(collector, amount, Long::sum);
        htsAdjustments.put(denominatingToken, denominatingTokenMap);
    }

    /**
     * Given a list of changes for a specific token, filters all credits and returns them
     * @param tokenIdChanges The list of changes for a specific token
     * @return The list of credits
     */
    public static Map<AccountID, Long> getFungibleTokenCredits(final Map<AccountID, Long> tokenIdChanges) {
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

    /**
     * Given a list of changes for a specific token, filters all fungible credits including hbar or
     * fungible token balances for a given beneficiary and returns them
     * @param result The {@link AssessmentResult} object
     * @param tokenId The token id
     * @param sender The sender of the nft
     * @return The list of credits
     */
    public static List<ExchangedValue> getFungibleCredits(
            final AssessmentResult result, final TokenID tokenId, final AccountID sender) {
        final var tokenChanges = result.getImmutableInputTokenAdjustments();
        // get all the fungible changes that are credited to the sender of nft in the same transaction.
        // this includes hbar and fungible token balances
        final var credits = new ArrayList<ExchangedValue>();
        for (final var entry : tokenChanges.entrySet()) {
            final var token = entry.getKey();
            final var map = entry.getValue();
            if (map.containsKey(sender) && map.get(sender) > 0) {
                credits.add(new ExchangedValue(sender, token, map.get(sender)));
            }
        }
        for (final var entry : result.getInputHbarAdjustments().entrySet()) {
            final var account = entry.getKey();
            final var amount = entry.getValue();
            if (amount > 0 && account.equals(sender)) {
                credits.add(new ExchangedValue(sender, null, amount));
            }
        }
        return credits;
    }

    public record ExchangedValue(AccountID account, TokenID tokenId, long amount) {}

    /**
     * Adjusts hbar fees. It makes 2 adjustments a debit for sender and credit to collector.
     * If there is already an entry merges the new change with it, otherwise creates a new entry.
     * @param result The {@link AssessmentResult} object
     * @param sender The sender account
     * @param hbarFee The {@link CustomFee} object
     */
    public static void adjustHbarFees(final AssessmentResult result, final AccountID sender, final CustomFee hbarFee) {
        final var hbarAdjustments = result.getHbarAdjustments();
        final var collector = hbarFee.feeCollectorAccountId();
        final var fixedSpec = hbarFee.fixedFee();
        if (fixedSpec != null) {
            final var amount = fixedSpec.amount();
            hbarAdjustments.merge(sender, -amount, Long::sum);
            hbarAdjustments.merge(collector, amount, Long::sum);
        }
    }
}
