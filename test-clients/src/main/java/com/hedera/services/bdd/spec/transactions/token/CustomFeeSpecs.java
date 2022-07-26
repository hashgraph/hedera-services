package com.hedera.services.bdd.spec.transactions.token;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.RoyaltyFee;

import java.util.OptionalLong;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.HapiPropertySource.asAccount;
import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.isIdLiteral;

public class CustomFeeSpecs {
	public static Function<HapiApiSpec, CustomFee> fixedHbarFee(long amount, String collector) {
		return spec -> builtFixedHbar(amount, collector, spec);
	}

	public static Function<HapiApiSpec, FixedFee> fixedHbarFeeInheritingRoyaltyCollector(long amount) {
		return spec -> builtFixedHbarSansCollector(amount);
	}

	public static Function<HapiApiSpec, CustomFee> fixedHtsFee(
			long amount,
			String denom,
			String collector
	) {
		return spec -> builtFixedHts(amount, denom, collector, spec);
	}

	public static Function<HapiApiSpec, FixedFee> fixedHtsFeeInheritingRoyaltyCollector(
			long amount,
			String denom
	) {
		return spec -> builtFixedHtsSansCollector(amount, denom, spec);
	}

	public static Function<HapiApiSpec, CustomFee> royaltyFeeNoFallback(
			long numerator,
			long denominator,
			String collector
	) {
		return spec -> builtRoyaltyNoFallback(numerator, denominator, collector, spec);
	}

	public static Function<HapiApiSpec, CustomFee> royaltyFeeWithFallback(
			long numerator,
			long denominator,
			Function<HapiApiSpec, FixedFee> fixedFallback,
			String collector
	) {
		return spec -> builtRoyaltyWithFallback(numerator, denominator, collector, fixedFallback, spec);
	}

	public static Function<HapiApiSpec, CustomFee> fractionalFee(
			long numerator,
			long denominator,
			long min,
			OptionalLong max,
			String collector
	) {
		return spec -> builtFractional(numerator, denominator, min, max, false, collector, spec);
	}

	public static Function<HapiApiSpec, CustomFee> fractionalFeeNetOfTransfers(
			long numerator,
			long denominator,
			long min,
			OptionalLong max,
			String collector
	) {
		return spec -> builtFractional(numerator, denominator, min, max, true, collector, spec);
	}

	public static Function<HapiApiSpec, CustomFee> incompleteCustomFee(String collector) {
		return spec -> buildIncompleteCustomFee(collector, spec);
	}

	static CustomFee buildIncompleteCustomFee(final String collector,
			final HapiApiSpec spec) {
		final var collectorId = isIdLiteral(collector) ? asAccount(collector) : spec.registry().getAccountID(collector);
		return CustomFee.newBuilder().setFeeCollectorAccountId(collectorId).build();
	}

	static CustomFee builtFixedHbar(
			long amount,
			String collector,
			HapiApiSpec spec
	) {
		return baseFixedBuilder(amount, collector, spec).build();
	}

	static FixedFee builtFixedHbarSansCollector(long amount) {
		return FixedFee.newBuilder().setAmount(amount).build();
	}

	static CustomFee builtRoyaltyNoFallback(
			long numerator,
			long denominator,
			String collector,
			HapiApiSpec spec
	) {
		final var feeCollector = TxnUtils.asId(collector, spec);
		return CustomFee.newBuilder()
				.setRoyaltyFee(baseRoyaltyBuilder(numerator, denominator))
				.setFeeCollectorAccountId(feeCollector)
				.build();
	}

	static CustomFee builtRoyaltyWithFallback(
			long numerator,
			long denominator,
			String collector,
			Function<HapiApiSpec, FixedFee> fixedFallback,
			HapiApiSpec spec
	) {
		final var feeCollector = TxnUtils.asId(collector, spec);
		final var fallback = fixedFallback.apply(spec);
		return CustomFee.newBuilder()
				.setRoyaltyFee(baseRoyaltyBuilder(numerator, denominator).setFallbackFee(fallback))
				.setFeeCollectorAccountId(feeCollector)
				.build();
	}

	static RoyaltyFee.Builder baseRoyaltyBuilder(long numerator, long denominator) {
		return RoyaltyFee.newBuilder()
				.setExchangeValueFraction(Fraction.newBuilder()
						.setNumerator(numerator)
						.setDenominator(denominator));
	}

	static CustomFee builtFixedHts(
			long amount,
			String denom,
			String collector,
			HapiApiSpec spec
	) {
		final var builder = baseFixedBuilder(amount, collector, spec);
		final var denomId = isIdLiteral(denom) ? asToken(denom) : spec.registry().getTokenID(denom);
		builder.getFixedFeeBuilder().setDenominatingTokenId(denomId);
		return builder.build();
	}

	static FixedFee builtFixedHtsSansCollector(
			long amount,
			String denom,
			HapiApiSpec spec
	) {
		final var denomId = TxnUtils.asTokenId(denom, spec);
		return FixedFee.newBuilder()
				.setAmount(amount)
				.setDenominatingTokenId(denomId)
				.build();
	}

	static CustomFee builtFractional(
			long numerator,
			long denominator,
			long min,
			OptionalLong max,
			boolean netOfTransfers,
			String collector,
			HapiApiSpec spec
	) {
		final var collectorId = isIdLiteral(collector)
				? asAccount(collector) : spec.registry().getAccountID(collector);
		final var fractionalBuilder = FractionalFee.newBuilder()
				.setFractionalAmount(Fraction.newBuilder()
						.setNumerator(numerator)
						.setDenominator(denominator))
				.setNetOfTransfers(netOfTransfers)
				.setMinimumAmount(min);
		max.ifPresent(fractionalBuilder::setMaximumAmount);
		return CustomFee.newBuilder()
				.setFractionalFee(fractionalBuilder)
				.setFeeCollectorAccountId(collectorId)
				.build();
	}

	static CustomFee.Builder baseFixedBuilder(
			long amount,
			String collector,
			HapiApiSpec spec
	) {
		final var collectorId = isIdLiteral(collector)
				? asAccount(collector) : spec.registry().getAccountID(collector);
		final var fixedBuilder = FixedFee.newBuilder()
				.setAmount(amount);
		final var builder = CustomFee.newBuilder()
				.setFixedFee(fixedBuilder)
				.setFeeCollectorAccountId(collectorId);
		return builder;
	}
}
