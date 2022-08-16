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
package com.hedera.services.fees;

import static com.hedera.services.calc.OverflowCheckingCalc.tinycentsToTinybars;
import static com.hedera.services.legacy.proto.utils.CommonUtils.productWouldOverflow;
import static com.hedera.services.sysfiles.ParsingUtils.fromTwoPartDelimited;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;

/**
 * Stores a parsed version of the {@code contract.storageSlotPriceTiers} property, which specifies
 * the price for renewing a single K/V pair for {@code contract.storageSlotLifetime} seconds. As
 * with all fee schedules metadata, the prices are denominated in tinycents (i.e.,
 * $0.00_000_000_001).
 *
 * <p>The maximum price that can be charged per second is {@code Long.MAX_VALUE /
 * contract.storageSlotLifetime}, but this will not be a limitation under any realistic scenario.
 */
public record ContractStoragePriceTiers(long[] usageTiers, long[] prices) {
    private static final int SLOT_SIZE = 64;
    public static final long TIER_MULTIPLIER = 1_000_000L;
    public static final long THOUSANDTHS_TO_TINY = 100_000_000L / 1_000L;

    public static ContractStoragePriceTiers from(final String propertyLiteral) {
        final var lastPrice = new AtomicLong(0);
        final var lastUsageTier = new AtomicLong(0);
        final List<Pair<Long, Long>> pricedTiers =
                Arrays.stream(propertyLiteral.split(","))
                        .map(
                                literal ->
                                        fromTwoPartDelimited(
                                                literal,
                                                "@",
                                                (price, usageTier) -> {
                                                    manageValidatedIncreasing(
                                                            "price", lastPrice, price);
                                                    manageValidatedIncreasing(
                                                            "usage tier", lastUsageTier, usageTier);
                                                },
                                                ContractStoragePriceTiers::parsePrice,
                                                ContractStoragePriceTiers::parseUsageTier,
                                                Pair::of))
                        .toList();
        return new ContractStoragePriceTiers(
                pricedTiers.stream().mapToLong(Pair::getValue).toArray(),
                pricedTiers.stream().mapToLong(Pair::getKey).toArray());
    }

    /**
     * Returns the price in tinybars to keep the given bytecode in storage for a given period. The
     * answer is calculated by treating the bytecode as the number of key/value pairs that the same
     * total bytes.
     *
     * @param numKvPairsUsed the number of total key/value pairs in network storage
     * @param rate the active exchange rate
     * @param slotLifetime the current value of contract.storageSlotLifetime
     * @param code the bytecode
     * @param requestedLifetime the desired period of an incremental key/value pair
     * @return the price in tinybars
     */
    public long codePrice(
            final ExchangeRate rate,
            final long slotLifetime,
            final long numKvPairsUsed,
            final Bytes code,
            final long requestedLifetime) {
        final var equivNumSlots = Math.max(1, code.size() / SLOT_SIZE);
        return slotPrice(rate, slotLifetime, numKvPairsUsed, equivNumSlots, requestedLifetime);
    }

    /**
     * Returns the price in tinybars to keep one key/value pair in contract storage for a given
     * period.
     *
     * @param numKvPairsUsed the number of total key/value pairs in network storage
     * @param rate the active exchange rate
     * @param slotLifetime the current value of contract.storageSlotLifetime
     * @param requestedKvPairs the desired number of new key/value pairs
     * @param requestedLifetime the desired period of an incremental key/value pair
     * @return the price in tinybars
     */
    public long slotPrice(
            final ExchangeRate rate,
            final long slotLifetime,
            final long numKvPairsUsed,
            final int requestedKvPairs,
            final long requestedLifetime) {
        assertValidArgs(requestedKvPairs, requestedLifetime);

        // Traverse tiers until we find the active one
        int i = 0;
        for (; numKvPairsUsed > usageTiers[i]; i++) {
            if (i == usageTiers.length - 1) {
                break;
            }
        }

        final var price = prices[i];
        var fee = cappedMultiplication(requestedLifetime / slotLifetime, price);
        final var leftoverLifetime = requestedLifetime % slotLifetime;
        if (leftoverLifetime > 0) {
            // Add prorated charge for partial lifetime
            fee =
                    cappedAddition(
                            fee,
                            nonDegenerateDiv(
                                    cappedMultiplication(leftoverLifetime, price), slotLifetime));
        }
        return Math.max(1, cappedMultiplication(tinycentsToTinybars(fee, rate), requestedKvPairs));
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
        if (o == null || ContractStoragePriceTiers.class != o.getClass()) {
            return false;
        }
        final var that = (ContractStoragePriceTiers) o;
        return Arrays.equals(this.usageTiers, that.usageTiers)
                && Arrays.equals(this.prices, that.prices);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(usageTiers) + Arrays.hashCode(prices);
    }

    @Override
    public String toString() {
        return "ContractStoragePriceTiers{"
                + "usageTiers="
                + Arrays.toString(usageTiers)
                + ", prices="
                + Arrays.toString(prices)
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

    private static void manageValidatedIncreasing(
            final String thing, final AtomicLong last, final long current) {
        if (last.get() >= current) {
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
