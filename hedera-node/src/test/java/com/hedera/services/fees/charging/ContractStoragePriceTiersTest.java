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
import static com.hedera.services.fees.charging.ContractStoragePriceTiers.THOUSANDTHS_TO_TINY;
import static com.hedera.services.fees.charging.ContractStoragePriceTiers.cappedAddition;
import static com.hedera.services.fees.charging.ContractStoragePriceTiers.cappedMultiplication;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.store.contracts.KvUsageInfo;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import org.junit.jupiter.api.Test;

class ContractStoragePriceTiersTest {

    private static final long M = ContractStoragePriceTiers.TIER_MULTIPLIER;
    private static final long P = THOUSANDTHS_TO_TINY;
    private static final int DEFAULT_FREE_TIER_LIMIT = 100;
    private static final long DEFAULT_MAX_KV_PAIRS = 500_000_000;
    private static final long DEFAULT_REFERENCE_LIFETIME = 2592000L;
    private static final String CANONICAL_SPEC =
            "10til50M,50til100M,100til150M,200til200M,500til250M,700til300M"
                    + ",1000til350M,2000til400M,5000til450M,10000til500M";

    private ContractStoragePriceTiers subject;

    @Test
    void doesntChargeZeroGivenNonZeroPrice() {
        givenTypicalSubject();
        final var degenerate =
                subject.priceOfPendingUsage(
                        degenRate, 1_000_000, DEFAULT_REFERENCE_LIFETIME, nonFreeUsageFor(1));
        assertEquals(1, degenerate);
    }

    @Test
    void chargesZeroGivenZeroPrice() {
        givenDefaultSubjectWith("0til100M");
        final var degenerate =
                subject.priceOfPendingUsage(
                        degenRate, 1_000_000, DEFAULT_REFERENCE_LIFETIME, nonFreeUsageFor(1));
        assertEquals(0, degenerate);
    }

    @Test
    void congestionPricingWorks() {
        givenDefaultSubjectWith("0til50M,2000til450M");
        final var used = 475_000_000L;
        final var congestionFactor = DEFAULT_MAX_KV_PAIRS / (DEFAULT_MAX_KV_PAIRS - used);
        final var basePrice = 2000 * THOUSANDTHS_TO_TINY;
        final var expected = tinycentsToTinybars(basePrice, someRate) * congestionFactor;

        final var actual =
                subject.priceOfPendingUsage(
                        someRate, used, DEFAULT_REFERENCE_LIFETIME, nonFreeUsageFor(1));

        assertEquals(expected, actual);
    }

    @Test
    void congestionPricingOnlyActivePastUpperLimit() {
        givenDefaultSubjectWith("0til50M,2000til450M");
        final var used = 450_000_000L;
        final var basePrice = 2000 * THOUSANDTHS_TO_TINY;
        final var expected = tinycentsToTinybars(basePrice, someRate);

        final var actual =
                subject.priceOfPendingUsage(
                        someRate, used, DEFAULT_REFERENCE_LIFETIME, nonFreeUsageFor(1));

        assertEquals(expected, actual);
    }

    @Test
    void autoRenewalWorksForNonFreeTiers() {
        givenDefaultSubjectWith("0til50M,2000til450M");
        final var used = 450_000_000L;
        final var basePrice = 2000 * THOUSANDTHS_TO_TINY;
        final var expected = 101 * tinycentsToTinybars(basePrice, someRate);

        final var actual =
                subject.priceOfAutoRenewal(someRate, used, DEFAULT_REFERENCE_LIFETIME, 101);

        assertEquals(expected, actual);
    }

    @Test
    void autoRenewalWorksForFreeTiers() {
        givenDefaultSubjectWith("0til50M,2000til450M");
        final var used = 450_000_000L;

        final var actual =
                subject.priceOfAutoRenewal(someRate, used, DEFAULT_REFERENCE_LIFETIME, 99);

        assertEquals(0, actual);
    }

    @Test
    void partialReferenceLifetimesWork() {
        givenDefaultSubjectWith("0til50M,2000til450M");
        final var used = 100_000_000L;
        final var basePrice = 2000 * THOUSANDTHS_TO_TINY;
        final var lifetime = DEFAULT_REFERENCE_LIFETIME / 4;
        final var expected = tinycentsToTinybars(basePrice / 4, someRate);

        final var actual =
                subject.priceOfPendingUsage(someRate, used, lifetime, nonFreeUsageFor(1));

        assertEquals(expected, actual);
    }

    @Test
    void promotionalOfferIsDetected() {
        givenDefaultSubjectWith("0til50M,2000til450M");
        assertTrue(subject.promotionalOfferCovers(50_000_000));
        assertFalse(subject.promotionalOfferCovers(50_000_001));
    }

    @Test
    void failsOnZeroSlotsRequested() {
        givenTypicalSubject();
        final var usage = nonFreeUsageFor(0);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        subject.priceOfPendingUsage(
                                someRate, 666, DEFAULT_REFERENCE_LIFETIME, usage));
    }

    @Test
    void failsOnNegativeLifetime() {
        givenTypicalSubject();
        final var info = nonFreeUsageFor(1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        subject.priceOfPendingUsage(
                                someRate, 666, -DEFAULT_REFERENCE_LIFETIME, info));
    }

    @Test
    void getsExpectedForCanonicalPeriod() {
        givenTypicalSubject();
        final var correctPrice = 100 * THOUSANDTHS_TO_TINY;
        final var expected = tinycentsToTinybars(correctPrice, someRate);

        final var actual =
                subject.priceOfPendingUsage(
                        someRate, 150_000_000, DEFAULT_REFERENCE_LIFETIME, nonFreeUsageFor(1));

        assertEquals(expected, actual);
    }

    @Test
    void getsExpectedForTwoAtHalfCanonicalPeriod() {
        givenTypicalSubject();
        final var correctPrice = 100 * THOUSANDTHS_TO_TINY;
        final var expected = tinycentsToTinybars(correctPrice, someRate);

        final var actual =
                subject.priceOfPendingUsage(
                        someRate, 150_000_000, DEFAULT_REFERENCE_LIFETIME / 2, nonFreeUsageFor(2));

        assertEquals(expected / 2 * 2, actual);
    }

    @Test
    void canUseFirstTier() {
        givenTypicalSubject();
        final var correctPrice = 10 * THOUSANDTHS_TO_TINY;
        final var expected = tinycentsToTinybars(correctPrice + 1, someRate);

        final var actual =
                subject.priceOfPendingUsage(
                        someRate, 1_000_000, DEFAULT_REFERENCE_LIFETIME + 1, nonFreeUsageFor(1));

        assertEquals(expected, actual);
    }

    @Test
    void costIsUnpayableWithNoSlotsRemaining() {
        givenDefaultSubjectWith("0til50M,2000til450M");

        final var actual =
                subject.priceOfPendingUsage(
                        someRate,
                        500_000_000,
                        3 * DEFAULT_REFERENCE_LIFETIME / 2,
                        nonFreeUsageFor(1));

        assertEquals(Long.MAX_VALUE, actual);
    }

    @Test
    void alwaysFreeIfInFreeTier() {
        givenTypicalSubject();

        final var actual =
                subject.priceOfPendingUsage(
                        someRate,
                        500_000_000,
                        3 * DEFAULT_REFERENCE_LIFETIME / 2,
                        usageInfoFor(DEFAULT_FREE_TIER_LIMIT - 10, 1));

        assertEquals(0, actual);
    }

    @Test
    void parsesValidAsExpected() {
        final var input =
                "10til50M,50til100M,100til150M,200til200M,500til250M,700til300M"
                        + ",1000til350M,2000til400M,5000til450M,10000til500M";
        final var expectedUsageTiers =
                new long[] {
                    50 * M, 100 * M, 150 * M, 200 * M, 250 * M, 300 * M, 350 * M, 400 * M, 450 * M,
                    500 * M
                };
        final var expectedPrices =
                new long[] {
                    10 * P, 50 * P, 100 * P, 200 * P, 500 * P, 700 * P, 1000 * P, 2000 * P,
                    5000 * P, 10000 * P
                };
        givenDefaultSubjectWith(input);

        assertArrayEquals(expectedUsageTiers, subject.usageTiers());
        assertArrayEquals(expectedPrices, subject.prices());
    }

    @Test
    void failsOnDecreasingPrice() {
        final var input = "10til50,9til100";

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ContractStoragePriceTiers.from(
                                input,
                                DEFAULT_FREE_TIER_LIMIT,
                                DEFAULT_MAX_KV_PAIRS,
                                DEFAULT_REFERENCE_LIFETIME));
    }

    @Test
    void failsOnDecreasingUsage() {
        final var input = "10til50,11til49";

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ContractStoragePriceTiers.from(
                                input,
                                DEFAULT_FREE_TIER_LIMIT,
                                DEFAULT_MAX_KV_PAIRS,
                                DEFAULT_REFERENCE_LIFETIME));
    }

    @Test
    void failsOnEmpty() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ContractStoragePriceTiers.from(
                                "",
                                DEFAULT_FREE_TIER_LIMIT,
                                DEFAULT_MAX_KV_PAIRS,
                                DEFAULT_REFERENCE_LIFETIME));
    }

    @Test
    @SuppressWarnings("java:S3415")
    void objectMethodsAsExpected() {
        final var a =
                ContractStoragePriceTiers.from(
                        "1til10,5til50,1000til100",
                        DEFAULT_FREE_TIER_LIMIT,
                        DEFAULT_MAX_KV_PAIRS,
                        DEFAULT_REFERENCE_LIFETIME);
        final var b =
                ContractStoragePriceTiers.from(
                        "1til10,5til50,10til100",
                        DEFAULT_FREE_TIER_LIMIT,
                        DEFAULT_MAX_KV_PAIRS,
                        DEFAULT_REFERENCE_LIFETIME);
        final var c =
                ContractStoragePriceTiers.from(
                        "1til10,5til50,1000til200",
                        DEFAULT_FREE_TIER_LIMIT,
                        DEFAULT_MAX_KV_PAIRS,
                        DEFAULT_REFERENCE_LIFETIME);
        final var d = a;
        final var e =
                ContractStoragePriceTiers.from(
                        "1til10,5til50,1000til100",
                        DEFAULT_FREE_TIER_LIMIT,
                        DEFAULT_MAX_KV_PAIRS,
                        DEFAULT_REFERENCE_LIFETIME);
        final var f =
                ContractStoragePriceTiers.from(
                        "1til10,5til50,1000til100",
                        DEFAULT_FREE_TIER_LIMIT - 1,
                        DEFAULT_MAX_KV_PAIRS,
                        DEFAULT_REFERENCE_LIFETIME);
        final var g =
                ContractStoragePriceTiers.from(
                        "1til10,5til50,1000til100",
                        DEFAULT_FREE_TIER_LIMIT,
                        DEFAULT_MAX_KV_PAIRS - 1,
                        DEFAULT_REFERENCE_LIFETIME);
        final var h =
                ContractStoragePriceTiers.from(
                        "1til10,5til50,1000til100",
                        DEFAULT_FREE_TIER_LIMIT,
                        DEFAULT_MAX_KV_PAIRS,
                        DEFAULT_REFERENCE_LIFETIME - 1);
        final var desired =
                "ContractStoragePriceTiers{usageTiers=[10, 50, 100], prices=[100000, 500000,"
                        + " 100000000], freeTierLimit=100, maxTotalKvPairs=500000000,"
                        + " referenceLifetime=2592000}";

        assertEquals(a, d);

        assertNotEquals(a, null);
        assertNotEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(a, f);
        assertNotEquals(a, g);
        assertNotEquals(a, h);
        assertEquals(a, e);
        // and:
        assertEquals(a.hashCode(), e.hashCode());
        // and:
        assertEquals(desired, a.toString());
    }

    @Test
    void cappedMultiplicationWorks() {
        assertEquals(2 * 10, cappedMultiplication(2, 10));
        assertEquals(Long.MAX_VALUE, cappedMultiplication(3, Long.MAX_VALUE / 2));
    }

    @Test
    void cappedAdditionWorks() {
        assertEquals(2 + 10, cappedAddition(2, 10));
        assertEquals(Long.MAX_VALUE, cappedAddition(3, Long.MAX_VALUE - 2));
    }

    private void givenTypicalSubject() {
        givenDefaultSubjectWith(CANONICAL_SPEC);
    }

    private void givenDefaultSubjectWith(final String spec) {
        givenSubjectWith(
                spec, DEFAULT_FREE_TIER_LIMIT, DEFAULT_MAX_KV_PAIRS, DEFAULT_REFERENCE_LIFETIME);
    }

    private void givenCanonicalSubjectWith(
            final int freeTierLimit, final long maxTotalKvPairs, final long referenceLifetime) {
        givenSubjectWith(CANONICAL_SPEC, freeTierLimit, maxTotalKvPairs, referenceLifetime);
    }

    private void givenSubjectWith(
            final String spec,
            final int freeTierLimit,
            final long maxTotalKvPairs,
            final long referenceLifetime) {
        subject =
                ContractStoragePriceTiers.from(
                        spec, freeTierLimit, maxTotalKvPairs, referenceLifetime);
    }

    private KvUsageInfo nonFreeUsageFor(final int delta) {
        final var info = new KvUsageInfo(DEFAULT_FREE_TIER_LIMIT + 1);
        info.updatePendingBy(delta);
        return info;
    }

    private KvUsageInfo usageInfoFor(final int current, final int delta) {
        final var info = new KvUsageInfo(current);
        info.updatePendingBy(delta);
        return info;
    }

    private static final ExchangeRate someRate =
            ExchangeRate.newBuilder().setHbarEquiv(12).setCentEquiv(123).build();
    private static final ExchangeRate degenRate =
            ExchangeRate.newBuilder().setHbarEquiv(1).setCentEquiv(Integer.MAX_VALUE).build();
}
