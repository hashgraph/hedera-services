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
package com.hedera.services.bdd.spec.transactions.token;

import static com.hedera.services.bdd.spec.HapiPropertySource.asAccount;
import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.isIdLiteral;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import java.util.OptionalLong;
import java.util.function.Function;

public class CustomFeeSpecs {
    public static Function<HapiSpec, CustomFee> fixedHbarFee(long amount, String collector) {
        return fixedHbarFee(amount, collector, false);
    }

    public static Function<HapiSpec, CustomFee> fixedHbarFee(
            long amount, String collector, boolean allCollectorsExempt) {
        return spec -> builtFixedHbar(amount, collector, allCollectorsExempt, spec);
    }

    public static Function<HapiSpec, FixedFee> fixedHbarFeeInheritingRoyaltyCollector(long amount) {
        return spec -> builtFixedHbarSansCollector(amount);
    }

    public static Function<HapiSpec, CustomFee> fixedHtsFee(
            long amount, String denom, String collector) {
        return fixedHtsFee(amount, denom, collector, false);
    }

    public static Function<HapiSpec, CustomFee> fixedHtsFee(
            long amount, String denom, String collector, boolean allCollectorsExempt) {
        return spec -> builtFixedHts(amount, denom, collector, allCollectorsExempt, spec);
    }

    public static Function<HapiSpec, FixedFee> fixedHtsFeeInheritingRoyaltyCollector(
            long amount, String denom) {
        return spec -> builtFixedHtsSansCollector(amount, denom, spec);
    }

    public static Function<HapiSpec, CustomFee> royaltyFeeNoFallback(
            long numerator, long denominator, String collector) {
        return royaltyFeeNoFallback(numerator, denominator, collector, false);
    }

    public static Function<HapiSpec, CustomFee> royaltyFeeNoFallback(
            long numerator, long denominator, String collector, boolean allCollectorsExempt) {
        return spec ->
                builtRoyaltyNoFallback(
                        numerator, denominator, collector, allCollectorsExempt, spec);
    }

    public static Function<HapiSpec, CustomFee> royaltyFeeWithFallback(
            long numerator,
            long denominator,
            Function<HapiSpec, FixedFee> fixedFallback,
            String collector) {
        return royaltyFeeWithFallback(numerator, denominator, fixedFallback, collector, false);
    }

    public static Function<HapiSpec, CustomFee> royaltyFeeWithFallback(
            long numerator,
            long denominator,
            Function<HapiSpec, FixedFee> fixedFallback,
            String collector,
            boolean allCollectorsExempt) {
        return spec ->
                builtRoyaltyWithFallback(
                        numerator,
                        denominator,
                        collector,
                        allCollectorsExempt,
                        fixedFallback,
                        spec);
    }

    public static Function<HapiSpec, CustomFee> fractionalFee(
            long numerator, long denominator, long min, OptionalLong max, String collector) {
        return fractionalFee(numerator, denominator, min, max, collector, false);
    }

    public static Function<HapiSpec, CustomFee> fractionalFeeNetOfTransfers(
            long numerator, long denominator, long min, OptionalLong max, String collector) {
        return fractionalFeeNetOfTransfers(numerator, denominator, min, max, collector, false);
    }

    public static Function<HapiSpec, CustomFee> fractionalFeeNetOfTransfers(
            long numerator,
            long denominator,
            long min,
            OptionalLong max,
            String collector,
            boolean allCollectorsExempt) {
        return spec ->
                builtFractional(
                        numerator,
                        denominator,
                        min,
                        max,
                        true,
                        collector,
                        allCollectorsExempt,
                        spec);
    }

    public static Function<HapiSpec, CustomFee> fractionalFee(
            long numerator,
            long denominator,
            long min,
            OptionalLong max,
            String collector,
            boolean allCollectorsExempt) {
        return spec ->
                builtFractional(
                        numerator,
                        denominator,
                        min,
                        max,
                        false,
                        collector,
                        allCollectorsExempt,
                        spec);
    }

    public static Function<HapiSpec, CustomFee> incompleteCustomFee(String collector) {
        return spec -> buildIncompleteCustomFee(collector, spec);
    }

    static CustomFee buildIncompleteCustomFee(final String collector, final HapiSpec spec) {
        final var collectorId =
                isIdLiteral(collector)
                        ? asAccount(collector)
                        : spec.registry().getAccountID(collector);
        return CustomFee.newBuilder().setFeeCollectorAccountId(collectorId).build();
    }

    static CustomFee builtFixedHbar(
            long amount, String collector, boolean allCollectorsExempt, HapiSpec spec) {
        return baseFixedBuilder(amount, collector, allCollectorsExempt, spec).build();
    }

    static FixedFee builtFixedHbarSansCollector(long amount) {
        return FixedFee.newBuilder().setAmount(amount).build();
    }

    static CustomFee builtRoyaltyNoFallback(
            long numerator,
            long denominator,
            String collector,
            boolean allCollectorsExempt,
            HapiSpec spec) {
        final var feeCollector = TxnUtils.asId(collector, spec);
        return CustomFee.newBuilder()
                .setRoyaltyFee(baseRoyaltyBuilder(numerator, denominator))
                .setFeeCollectorAccountId(feeCollector)
                .setAllCollectorsAreExempt(allCollectorsExempt)
                .build();
    }

    static CustomFee builtRoyaltyWithFallback(
            long numerator,
            long denominator,
            String collector,
            boolean allCollectorsExempt,
            Function<HapiSpec, FixedFee> fixedFallback,
            HapiSpec spec) {
        final var feeCollector = TxnUtils.asId(collector, spec);
        final var fallback = fixedFallback.apply(spec);
        return CustomFee.newBuilder()
                .setRoyaltyFee(baseRoyaltyBuilder(numerator, denominator).setFallbackFee(fallback))
                .setFeeCollectorAccountId(feeCollector)
                .setAllCollectorsAreExempt(allCollectorsExempt)
                .build();
    }

    static RoyaltyFee.Builder baseRoyaltyBuilder(long numerator, long denominator) {
        return RoyaltyFee.newBuilder()
                .setExchangeValueFraction(
                        Fraction.newBuilder().setNumerator(numerator).setDenominator(denominator));
    }

    static CustomFee builtFixedHts(
            long amount,
            String denom,
            String collector,
            boolean allCollectorsExempt,
            HapiSpec spec) {
        final var builder = baseFixedBuilder(amount, collector, allCollectorsExempt, spec);
        final var denomId = isIdLiteral(denom) ? asToken(denom) : spec.registry().getTokenID(denom);
        builder.getFixedFeeBuilder().setDenominatingTokenId(denomId);
        return builder.build();
    }

    static FixedFee builtFixedHtsSansCollector(long amount, String denom, HapiSpec spec) {
        final var denomId = TxnUtils.asTokenId(denom, spec);
        return FixedFee.newBuilder().setAmount(amount).setDenominatingTokenId(denomId).build();
    }

    static CustomFee builtFractional(
            long numerator,
            long denominator,
            long min,
            OptionalLong max,
            boolean netOfTransfers,
            String collector,
            boolean allCollectorsExempt,
            HapiSpec spec) {
        final var collectorId =
                isIdLiteral(collector)
                        ? asAccount(collector)
                        : spec.registry().getAccountID(collector);
        final var fractionalBuilder =
                FractionalFee.newBuilder()
                        .setFractionalAmount(
                                Fraction.newBuilder()
                                        .setNumerator(numerator)
                                        .setDenominator(denominator))
                        .setNetOfTransfers(netOfTransfers)
                        .setMinimumAmount(min);
        max.ifPresent(fractionalBuilder::setMaximumAmount);
        return CustomFee.newBuilder()
                .setFractionalFee(fractionalBuilder)
                .setFeeCollectorAccountId(collectorId)
                .setAllCollectorsAreExempt(allCollectorsExempt)
                .build();
    }

    static CustomFee.Builder baseFixedBuilder(
            long amount, String collector, boolean allCollectorsExempt, HapiSpec spec) {
        final var collectorId =
                isIdLiteral(collector)
                        ? asAccount(collector)
                        : spec.registry().getAccountID(collector);
        final var fixedBuilder = FixedFee.newBuilder().setAmount(amount);
        return CustomFee.newBuilder()
                .setAllCollectorsAreExempt(allCollectorsExempt)
                .setFixedFee(fixedBuilder)
                .setFeeCollectorAccountId(collectorId);
    }
}
