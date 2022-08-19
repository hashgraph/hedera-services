/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.fees.charging;

import static com.hedera.services.calc.OverflowCheckingCalc.tinycentsToTinybars;
import static com.hedera.services.legacy.proto.utils.CommonUtils.productWouldOverflow;
import static com.hedera.services.sysfiles.ParsingUtils.fromTwoPartDelimited;

import com.hedera.services.store.contracts.KvUsageInfo;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Stores a parsed version of the {@code contract.storageSlotPriceTiers} property, which specifies
 * the price for renewing a single K/V pair for a reference lifetime, at different levels of overall
 * and per-contract storage utilization. Note that the final price tier is subject to congestion
 * pricing, in which the base price is scaled by a factor of {@code 1 / (1 - d)} where {@code d = (#
 * K/V pairs used) / (max # K/V)}.
 */
public record ContractStoragePriceTiers(
        long[] usageTiers,
        long[] prices,
        int freeTierLimit,
        long maxTotalKvPairs,
        long referenceLifetime) {

    public static final long TIER_MULTIPLIER = 1_000_000L;
    public static final long THOUSANDTHS_TO_TINY = 100_000_000L / 1_000L;

    public static ContractStoragePriceTiers from(
            final String spec,
            final int freeTierLimit,
            final long maxTotalKvPairs,
            final long referenceLifetime) {
        final var lastPrice = new AtomicLong(0);
        final var lastUsageTier = new AtomicLong(0);
        final List<Pair<Long, Long>> pricedTiers =
                Arrays.stream(spec.split(","))
                        .map(
                                literal ->
                                        fromTwoPartDelimited(
                                                literal,
                                                "til",
                                                (price, usageTier) -> {
                                                    manageValidatedNonDecreasing(
                                                            "price", lastPrice, price);
                                                    manageValidatedNonDecreasing(
                                                            "usage tier", lastUsageTier, usageTier);
                                                },
                                                ContractStoragePriceTiers::parsePrice,
                                                ContractStoragePriceTiers::parseUsageTier,
                                                Pair::of))
                        .toList();
        return new ContractStoragePriceTiers(
                pricedTiers.stream().mapToLong(Pair::getValue).toArray(),
                pricedTiers.stream().mapToLong(Pair::getKey).toArray(),
                freeTierLimit,
                maxTotalKvPairs,
                referenceLifetime);
    }

    public boolean promotionalOfferCovers(final long totalKvPairsUsed) {
        return prices[0] == 0 && totalKvPairsUsed <= usageTiers[0];
    }

    /**
     * Returns the price in tinybars to keep one key/value pair in contract storage for a given
     * period.
     *
     * @param totalKvPairsUsed the number of total key/value pairs in network storage
     * @param rate the active exchange rate
     * @param requestedLifetime the desired period of an incremental key/value pair
     * @param usageInfo the information on the pending usage to be charged
     * @return the price in tinybars
     */
    public long priceOfPendingUsage(
            final ExchangeRate rate,
            final long totalKvPairsUsed,
            final long requestedLifetime,
            final KvUsageInfo usageInfo) {
        return priceOfUsage(
                rate,
                totalKvPairsUsed,
                requestedLifetime,
                usageInfo.pendingUsage(),
                usageInfo.pendingUsageDelta());
    }

    public long priceOfAutoRenewal(
            final ExchangeRate rate,
            final long totalKvPairsUsed,
            final long requestedLifetime,
            final KvUsageInfo usageInfo) {
        return priceOfUsage(
                rate,
                totalKvPairsUsed,
                requestedLifetime,
                usageInfo.pendingUsage(),
                usageInfo.currentUsage());
    }

    private long priceOfUsage(
            final ExchangeRate rate,
            final long totalKvPairsUsed,
            final long requestedLifetime,
            final long pendingUsage,
            final long requestedKvPairs) {
        assertValidArgs(requestedKvPairs, requestedLifetime);
        if (pendingUsage < freeTierLimit) {
            return 0;
        }

        // Traverse tiers until we find the active one
        int i = 0;
        for (; totalKvPairsUsed > usageTiers[i]; i++) {
            if (i == usageTiers.length - 1) {
                break;
            }
        }

        final var price = prices[i];
        if (price == 0) {
            return 0;
        }
        var fee = cappedMultiplication(requestedLifetime / referenceLifetime, price);
        final var leftoverLifetime = requestedLifetime % referenceLifetime;
        if (leftoverLifetime > 0) {
            // Add prorated charge for partial lifetime
            fee =
                    cappedAddition(
                            fee,
                            nonDegenerateDiv(
                                    cappedMultiplication(leftoverLifetime, price),
                                    referenceLifetime));
        }
        var cost =
                Math.max(1, cappedMultiplication(tinycentsToTinybars(fee, rate), requestedKvPairs));
        if (i == usageTiers.length - 1) {
            // Congestion pricing takes effect now
            final var slotsRemaining = maxTotalKvPairs - totalKvPairsUsed;
            cost =
                    slotsRemaining > 0
                            ? cappedMultiplication(cost, maxTotalKvPairs / slotsRemaining)
                            : Long.MAX_VALUE;
        }
        return cost;
    }

    private void assertValidArgs(final long requestedKvPairs, final long requestedLifetime) {
        if (requestedKvPairs <= 0) {
            throw new IllegalArgumentException("Must request a positive number of slots");
        }
        if (requestedLifetime < 0) {
            throw new IllegalArgumentException("Must request a non-negative lifetime");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ContractStoragePriceTiers that = (ContractStoragePriceTiers) o;
        return freeTierLimit == that.freeTierLimit
                && maxTotalKvPairs == that.maxTotalKvPairs
                && referenceLifetime == that.referenceLifetime
                && Arrays.equals(usageTiers, that.usageTiers)
                && Arrays.equals(prices, that.prices);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(freeTierLimit, maxTotalKvPairs, referenceLifetime);
        result = 31 * result + Arrays.hashCode(usageTiers);
        result = 31 * result + Arrays.hashCode(prices);
        return result;
    }

    @Override
    public String toString() {
        return "ContractStoragePriceTiers{"
                + "usageTiers="
                + Arrays.toString(usageTiers)
                + ", prices="
                + Arrays.toString(prices)
                + ", freeTierLimit="
                + freeTierLimit
                + ", maxTotalKvPairs="
                + maxTotalKvPairs
                + ", referenceLifetime="
                + referenceLifetime
                + '}';
    }

    static long cappedMultiplication(final long a, final long b) {
        if (productWouldOverflow(a, b)) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }

    static long cappedAddition(final long a, final long b) {
        final var nominal = a + b;
        return nominal >= 0 ? nominal : Long.MAX_VALUE;
    }

    private static void manageValidatedNonDecreasing(
            final String thing, final AtomicLong last, final long current) {
        if (last.get() > current) {
            throw new IllegalArgumentException(
                    thing + " price cannot decrease (" + last.get() + " to " + current + ")");
        }
        last.set(current);
    }

    private static long parseUsageTier(final String s) {
        final var isMillions = s.endsWith("M");
        return parseScaled(
                isMillions ? s.substring(0, s.length() - 1) : s, isMillions ? TIER_MULTIPLIER : 1);
    }

    private static long parsePrice(final String s) {
        return parseScaled(s, THOUSANDTHS_TO_TINY);
    }

    private static long parseScaled(final String s, final long scale) {
        return Long.parseLong(s) * scale;
    }

    private static long nonDegenerateDiv(long dividend, long divisor) {
        return (dividend == 0) ? 0 : Math.max(1, dividend / divisor);
    }
}
