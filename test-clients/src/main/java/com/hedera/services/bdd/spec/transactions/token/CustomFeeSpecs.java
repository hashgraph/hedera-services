package com.hedera.services.bdd.spec.transactions.token;

import com.google.protobuf.UInt64Value;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hederahashgraph.api.proto.java.Fraction;
import proto.CustomFeesOuterClass;

import java.util.OptionalLong;
import java.util.function.Function;

public class CustomFeeSpecs {
	public static Function<HapiApiSpec, CustomFeesOuterClass.CustomFee> fixedHbarFee(long amount, String collector) {
		return spec -> builtFixedHbar(amount, collector, spec);
	}

	public static Function<HapiApiSpec, CustomFeesOuterClass.CustomFee> fixedHtsFee(
			long amount,
			String denom,
			String collector
	) {
		return spec -> builtFixedHts(amount, denom, collector, spec);
	}

	public static Function<HapiApiSpec, CustomFeesOuterClass.CustomFee> fractionalFee(
			long numerator,
			long denominator,
			long min,
			OptionalLong max,
			String collector
	) {
		return spec -> builtFractional(numerator, denominator, min, max, collector, spec);
	}

	static CustomFeesOuterClass.CustomFee builtFixedHbar(
			long amount,
			String collector,
			HapiApiSpec spec
	) {
		return baseFixedBuilder(amount, collector, spec).build();
	}

	static CustomFeesOuterClass.CustomFee builtFixedHts(
			long amount,
			String denom,
			String collector,
			HapiApiSpec spec
	) {
		final var builder = baseFixedBuilder(amount, collector, spec);
		final var denomId = spec.registry().getTokenID(denom);
		builder.getFixedFeeBuilder().setTokenId(denomId);
		return builder.build();
	}

	static CustomFeesOuterClass.CustomFee builtFractional(
			long numerator,
			long denominator,
			long min,
			OptionalLong max,
			String collector,
			HapiApiSpec spec
	) {
		final var collectorId = spec.registry().getAccountID(collector);
		final var fractionalBuilder = CustomFeesOuterClass.FractionalFee.newBuilder()
				.setFractionOfUnitsToCollect(Fraction.newBuilder()
						.setNumerator(numerator)
						.setDenominator(denominator))
				.setMinimumUnitsToCollect(min);
		max.ifPresent(l -> fractionalBuilder.setMaximumUnitsToCollect(UInt64Value.newBuilder().setValue(l)));
		return  CustomFeesOuterClass.CustomFee.newBuilder()
				.setFractionalFee(fractionalBuilder)
				.setFeeCollector(collectorId)
				.build();
	}

	static CustomFeesOuterClass.CustomFee.Builder baseFixedBuilder(
			long amount,
			String collector,
			HapiApiSpec spec
	) {
		final var collectorId = spec.registry().getAccountID(collector);
		final var fixedBuilder = CustomFeesOuterClass.FixedFee.newBuilder()
				.setUnitsToCollect(amount);
		final var builder = CustomFeesOuterClass.CustomFee.newBuilder()
				.setFixedFee(fixedBuilder)
				.setFeeCollector(collectorId);
		return builder;
	}
}
