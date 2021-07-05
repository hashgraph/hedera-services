package com.hedera.services.pricing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

public class AssetsLoader {
	private static final String CAPACITIES_RESOURCE = "capacities.json";
	private static final String CONSTANT_WEIGHTS_RESOURCE = "constant-weights.json";
	private static final String CANONICAL_PRICES_RESOURCE = "canonical-prices.json";

	public Map<HederaFunctionality, BigDecimal> loadConstWeights() throws IOException {
		try (var fin = AssetsLoader.class.getClassLoader().getResourceAsStream(CONSTANT_WEIGHTS_RESOURCE)) {
			final var om = new ObjectMapper();
			final var constWeights = om.readValue(fin, Map.class);

			final Map<HederaFunctionality, BigDecimal> typedConstWeights = new EnumMap<>(HederaFunctionality.class);
			constWeights.forEach((funcName, weight) -> {
				final var function = HederaFunctionality.valueOf((String) funcName);
				final var bdWeight = (weight instanceof Double)
						? BigDecimal.valueOf((Double) weight)
						: BigDecimal.valueOf((Integer) weight);
				typedConstWeights.put(function, bdWeight);
			});

			return typedConstWeights;
		}
	}

	public Map<UsableResource, BigDecimal> loadCapacities() throws IOException {
		try (var fin = AssetsLoader.class.getClassLoader().getResourceAsStream(CAPACITIES_RESOURCE)) {
			final var om = new ObjectMapper();
			final var capacities = om.readValue(fin, Map.class);

			final Map<UsableResource, BigDecimal> typedCapacities = new EnumMap<>(UsableResource.class);
			capacities.forEach((resourceName, amount) -> {
				final var resource = UsableResource.valueOf((String) resourceName);
				final var bdAmount = (amount instanceof Long)
						? BigDecimal.valueOf((Long) amount)
						: BigDecimal.valueOf((Integer) amount);
				typedCapacities.put(resource, bdAmount);
			});

			return typedCapacities;
		}
	}

	public Map<HederaFunctionality, Map<SubType, BigDecimal>> loadCanonicalPrices() throws IOException {
		try (var fin = AssetsLoader.class.getClassLoader().getResourceAsStream(CANONICAL_PRICES_RESOURCE)) {
			final var om = new ObjectMapper();
			final var prices = om.readValue(fin, Map.class);

			final Map<HederaFunctionality, Map<SubType, BigDecimal>> typedPrices =
					new EnumMap<>(HederaFunctionality.class);
			prices.forEach((funName, priceMap) -> {
				final var function = HederaFunctionality.valueOf((String) funName);
				final Map<SubType, BigDecimal> scopedPrices = new EnumMap<>(SubType.class);
				((Map) priceMap).forEach((typeName, price) -> {
					final var type = SubType.valueOf((String) typeName);
					final var bdPrice = (price instanceof Double)
							? BigDecimal.valueOf((Double) price)
							: BigDecimal.valueOf((Integer) price);
					scopedPrices.put(type, bdPrice);
				});
				typedPrices.put(function, scopedPrices);
			});

			return typedPrices;
		}
	}
}
