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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

public class AdjustmentUtils {
    private AdjustmentUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static long safeFractionMultiply(final long n, final long d, final long v) {
        if (v != 0 && n > Long.MAX_VALUE / v) {
            return BigInteger.valueOf(v)
                    .multiply(BigInteger.valueOf(n))
                    .divide(BigInteger.valueOf(d))
                    .longValueExact();
        } else {
            return n * v / d;
        }
    }

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

    public static void adjustHtsFees(
            final Map<TokenID, Map<AccountID, Long>> htsAdjustments,
            final AccountID sender,
            final AccountID collector,
            final CustomFeeMeta chargingTokenMeta,
            final long amount,
            final TokenID denominatingToken,
            final Set<TokenID> exemptDenoms) {
        if (amount < 0) {
            // Always add a new change for an HTS debit since it could trigger another assessed fee
            addHtsAdjustment(htsAdjustments, sender, collector, amount, denominatingToken);
            // self denominated fees are exempt from further fee charging
            if (chargingTokenMeta.tokenId().equals(denominatingToken)) {
                exemptDenoms.add(denominatingToken);
            }
        } else {
            addHtsAdjustment(htsAdjustments, sender, collector, amount, denominatingToken);
        }
    }

    private static void addHtsAdjustment(
            final Map<TokenID, Map<AccountID, Long>> htsAdjustments,
            final AccountID sender,
            final AccountID collector,
            final long amount,
            final TokenID denominatingToken) {
        final var denominatingTokenMap = htsAdjustments.get(denominatingToken);
        denominatingTokenMap.merge(sender, -amount, Long::sum);
        denominatingTokenMap.merge(collector, amount, Long::sum);
        htsAdjustments.put(denominatingToken, denominatingTokenMap);
    }

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

    public static Map<AccountID, Pair<Long, TokenID>> getFungibleCredits(
            final AssessmentResult result, final TokenID tokenId, final AccountID beneficiary) {
        final var tokenChanges = result.getInputTokenAdjustments().getOrDefault(tokenId, new HashMap<>());
        final var credits = new HashMap<AccountID, Pair<Long, TokenID>>();
        for (final var entry : tokenChanges.entrySet()) {
            final var account = entry.getKey();
            final var amount = entry.getValue();
            if (amount > 0 && account.equals(beneficiary)) {
                credits.put(account, Pair.of(amount, tokenId));
            }
        }
        for (final var entry : result.getInputHbarAdjustments().entrySet()) {
            final var account = entry.getKey();
            final var amount = entry.getValue();
            if (amount > 0 && account.equals(beneficiary)) {
                credits.put(account, Pair.of(amount, null));
            }
        }
        return credits;
    }

    public static void adjustHbarFees(final AssessmentResult result, final AccountID sender, final CustomFee hbarFee) {
        final var hbarAdjustments = result.getHbarAdjustments();
        final var collector = hbarFee.feeCollectorAccountId();
        final var fixedSpec = hbarFee.fixedFee();
        final var amount = fixedSpec.amount();
        hbarAdjustments.merge(sender, -amount, Long::sum);
        hbarAdjustments.merge(collector, amount, Long::sum);
    }

    /**
     * Checks if the adjustment will trigger a custom fee.
     * Custom fee is triggered if the fee is not self-denominated and the transfer is not a hbar transfer
     * and the adjustment is a debit.
     *
     * @param chargingTokenId         the token that is being charged
     * @param denominatingTokenID     the token that is being used as denomination to pay the fee
     * @param exemptDebits            the set of tokens that are exempt from custom fee charging, due to being self
     *                                denominated in previous level of custom fee assessment
     * @return true if the adjustment will trigger a custom fee. False otherwise.
     */
    public static boolean couldTriggerCustomFees(
            @NonNull final TokenID chargingTokenId,
            @NonNull final TokenID denominatingTokenID,
            @NonNull final Set<TokenID> exemptDebits) {
        if (isExemptFromCustomFees(chargingTokenId, denominatingTokenID, exemptDebits)) {
            return false;
        } else {
            // This condition is reached only for NftTransfer or Fungible token transfer with adjustment < 0
            return true;
        }
    }

    /**
     * Custom fee that is self-denominated is exempt from further custom fee charging.
     *
     * @param chargingTokenId     the token that is being charged
     * @param denominatingTokenID the token that is being used as denomination to pay the fee
     * @param exemptDebits
     * @return true if the custom fee is self-denominated
     */
    private static boolean isExemptFromCustomFees(
            @NonNull final TokenID chargingTokenId,
            @NonNull final TokenID denominatingTokenID,
            @NonNull final Set<TokenID> exemptDebits) {
        /* But self-denominated fees are exempt from further custom fee charging,
        c.f. https://github.com/hashgraph/hedera-services/issues/1925 */

        // Exempt debits will have the denominating token Ids from previous custom fee charging
        // So if the denominating tokenId is in the exempt debits, then it is self-denominated

        return denominatingTokenID != TokenID.DEFAULT && chargingTokenId.equals(denominatingTokenID)
                || exemptDebits.contains(denominatingTokenID);
    }
}
