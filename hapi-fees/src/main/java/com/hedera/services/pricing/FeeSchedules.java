package com.hedera.services.pricing;

import com.hedera.services.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static com.hedera.services.pricing.UsableResource.CONSTANT;
import static java.math.MathContext.DECIMAL128;
import static java.math.RoundingMode.HALF_UP;

public class FeeSchedules {
	static final BigDecimal USD_TO_TINYCENTS = BigDecimal.valueOf(100 * 100_000_000L);
	static final BigDecimal FEE_SCHEDULE_MULTIPLIER = BigDecimal.valueOf(1_000L);

	private static final AssetsLoader ASSETS_LOADER = new AssetsLoader();
	private static final CanonicalOperations CANONICAL_OPS = new CanonicalOperations();

	public Map<ResourceProvider, Map<UsableResource, Long>> canonicalPricesFor(
			HederaFunctionality function,
			SubType type
	) throws IOException {
		final var canonicalUsage = CANONICAL_OPS.canonicalUsageFor(function, type);
		final var genericPrices = genericPricesFor(function);
		final var genericPrice = computeGenericGiven(canonicalUsage, genericPrices);
		final var canonicalPrice = ASSETS_LOADER.loadCanonicalPrices().get(function).get(type);

		final var normalizingFactor = FEE_SCHEDULE_MULTIPLIER
				.multiply(canonicalPrice)
				.divide(genericPrice, DECIMAL128)
				.multiply(USD_TO_TINYCENTS);

		return canonicalPricesGiven(normalizingFactor, genericPrices);
	}

	private Map<ResourceProvider, Map<UsableResource, Long>> canonicalPricesGiven(
			BigDecimal normalizingFactor,
			Map<ResourceProvider, Map<UsableResource, BigDecimal>> genericPrices
	) {
		final Map<ResourceProvider, Map<UsableResource, Long>> canonicalPrices = new EnumMap<>(ResourceProvider.class);
		for (var provider : ResourceProvider.class.getEnumConstants()) {
			final Map<UsableResource, Long> providerPrices = new EnumMap<>(UsableResource.class);
			final var providerGenerics = genericPrices.get(provider);
			for (var resource : UsableResource.class.getEnumConstants()) {
				final var genericPrice = providerGenerics.get(resource);
				final var exactCanonicalPrice = normalizingFactor.multiply(genericPrice);
				final var canonicalPrice = exactCanonicalPrice.setScale(0, HALF_UP).longValueExact();
				providerPrices.put(resource, canonicalPrice);
			}
			canonicalPrices.put(provider, providerPrices);
		}
		return canonicalPrices;
	}


	private BigDecimal computeGenericGiven(
			UsageAccumulator canonicalUsage,
			Map<ResourceProvider, Map<UsableResource, BigDecimal>> genericPrices
	) {
		var sum = BigDecimal.ZERO;
		for (var provider : ResourceProvider.class.getEnumConstants()) {
			final var providerGenerics = genericPrices.get(provider);
			for (var resource : UsableResource.class.getEnumConstants()) {
				final var bdUsage = BigDecimal.valueOf(canonicalUsage.get(provider, resource));
				sum = sum.add(providerGenerics.get(resource).multiply(bdUsage));
			}
		}
		return sum;
	}

	private Map<ResourceProvider, Map<UsableResource, BigDecimal>> genericPricesFor(
			HederaFunctionality function
	) throws IOException {
		final var capacities = ASSETS_LOADER.loadCapacities();
		final var constW = ASSETS_LOADER.loadConstWeights().get(function);
		final var oneMinusConstW = BigDecimal.ONE.subtract(constW);

		final Map<ResourceProvider, Map<UsableResource, BigDecimal>> generics = new EnumMap<>(ResourceProvider.class);
		for (var provider : ResourceProvider.class.getEnumConstants()) {
			final Map<UsableResource, BigDecimal> providerGenerics = new EnumMap<>(UsableResource.class);

			var nonConstantGenerics = BigDecimal.ZERO;
			for (var resource : UsableResource.class.getEnumConstants()) {
				final var scale = BigDecimal.valueOf(provider.multiplier());
				final var capacity = capacities.get(resource);
				final var generic = BigDecimal.ONE.divide(capacity, DECIMAL128).multiply(scale);
				providerGenerics.put(resource, generic);
				if (resource != CONSTANT) {
					nonConstantGenerics = nonConstantGenerics.add(generic);
				}
			}
			providerGenerics.put(CONSTANT, nonConstantGenerics.multiply(constW).divide(oneMinusConstW, DECIMAL128));

			generics.put(provider, providerGenerics);
		}
		return generics;
	}
}
