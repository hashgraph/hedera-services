package com.hedera.services.pricing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hedera.services.pricing.FeeSchedules.FEE_SCHEDULE_MULTIPLIER;
import static com.hedera.services.pricing.FeeSchedules.USD_TO_TINYCENTS;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static java.math.MathContext.DECIMAL128;
import static java.math.RoundingMode.HALF_EVEN;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FeeSchedulesTest {
	private static final String FEE_SCHEDULE_FEES_KEY = "fees";
	private static final String FEE_SCHEDULE_TYPE_KEY = "subType";
	private static final String FEE_SCHEDULE_ENTRY_KEY = "transactionFeeSchedule";
	private static final String FEE_SCHEDULE_FUNCTION_KEY = "hederaFunctionality";

	private final FeeSchedules subject = new FeeSchedules();
	private final AssetsLoader assetsLoader = new AssetsLoader();
	private final CanonicalOperations canonicalOperations = new CanonicalOperations();

	@Test
	void computesExpectedPriceForFeeScheduleUpdate() throws IOException {
		// setup:
		final var canonicalPrices = assetsLoader.loadCanonicalPrices();
		final var expectedBasePrice = canonicalPrices.get(TokenFeeScheduleUpdate).get(DEFAULT);
		final var desired = "{\n" +
				"  \"transactionFeeSchedule\" : {\n" +
				"    \"hederaFunctionality\" : \"TokenFeeScheduleUpdate\",\n" +
				"    \"fees\" : [ {\n" +
				"      \"nodedata\" : {\n" +
				"        \"constant\" : 74935946,\n" +
				"        \"bpt\" : 119800,\n" +
				"        \"vpt\" : 299500304,\n" +
				"        \"rbh\" : 80,\n" +
				"        \"sbh\" : 6,\n" +
				"        \"gas\" : 799,\n" +
				"        \"bpr\" : 119800,\n" +
				"        \"sbpr\" : 2995,\n" +
				"        \"min\" : 0,\n" +
				"        \"max\" : 1000000000000000\n" +
				"      },\n" +
				"      \"networkdata\" : {\n" +
				"        \"constant\" : 1498718921,\n" +
				"        \"bpt\" : 2396002,\n" +
				"        \"vpt\" : 5990006087,\n" +
				"        \"rbh\" : 1597,\n" +
				"        \"sbh\" : 120,\n" +
				"        \"gas\" : 15973,\n" +
				"        \"bpr\" : 2396002,\n" +
				"        \"sbpr\" : 59900,\n" +
				"        \"min\" : 0,\n" +
				"        \"max\" : 1000000000000000\n" +
				"      },\n" +
				"      \"servicedata\" : {\n" +
				"        \"constant\" : 1498718921,\n" +
				"        \"bpt\" : 2396002,\n" +
				"        \"vpt\" : 5990006087,\n" +
				"        \"rbh\" : 1597,\n" +
				"        \"sbh\" : 120,\n" +
				"        \"gas\" : 15973,\n" +
				"        \"bpr\" : 2396002,\n" +
				"        \"sbpr\" : 59900,\n" +
				"        \"min\" : 0,\n" +
				"        \"max\" : 1000000000000000\n" +
				"      }\n" +
				"    } ]\n" +
				"  }\n" +
				"}";

		// given:
		Map<ResourceProvider, Map<UsableResource, Long>> computedPrices =
				subject.canonicalPricesFor(TokenFeeScheduleUpdate, DEFAULT);
		// and:
		final var canonicalUsage = canonicalOperations.canonicalUsageFor(TokenFeeScheduleUpdate, DEFAULT);
		final var jsonRepr = reprAsSingleFeeScheduleEntry(TokenFeeScheduleUpdate, DEFAULT, computedPrices);

		// when:
		final var actualBasePrice = feeInUsd(computedPrices, canonicalUsage);

		// then:
		assertEquals(expectedBasePrice.doubleValue(), actualBasePrice.doubleValue());
		assertEquals(desired, jsonRepr);
	}

	@Test
	void computesExpectedPriceForSubmitMessage() throws IOException {
		// setup:
		final var canonicalPrices = assetsLoader.loadCanonicalPrices();
		final var expectedBasePrice = canonicalPrices.get(ConsensusSubmitMessage).get(DEFAULT);

		// given:
		Map<ResourceProvider, Map<UsableResource, Long>> computedPrices =
				subject.canonicalPricesFor(ConsensusSubmitMessage, DEFAULT);
		// and:
		final var canonicalUsage = canonicalOperations.canonicalUsageFor(ConsensusSubmitMessage, DEFAULT);

		// when:
		final var actualBasePrice = feeInUsd(computedPrices, canonicalUsage);

		// then:
		assertEquals(expectedBasePrice.doubleValue(), actualBasePrice.doubleValue());
	}


	private String reprAsSingleFeeScheduleEntry(
			HederaFunctionality function,
			SubType type,
			Map<ResourceProvider, Map<UsableResource, Long>> prices
	) throws JsonProcessingException {
		final Map<String, Object> transactionFeeSchedule = new HashMap<>();

		final Map<String, Object> details = new LinkedHashMap<>();
		details.put(FEE_SCHEDULE_FUNCTION_KEY, function.toString());
		final Map<String, Object> scopedPrices = new LinkedHashMap<>();
		if (type != DEFAULT) {
			scopedPrices.put(FEE_SCHEDULE_TYPE_KEY, type.toString());
		}
		for (var provider : ResourceProvider.class.getEnumConstants()) {
			final Map<String, Long> constrainedPrices = new LinkedHashMap<>();
			final var providerPrices = prices.get(provider);
			for (var resource : UsableResource.class.getEnumConstants()) {
				final var price = providerPrices.get(resource);
				constrainedPrices.put(resource.toString().toLowerCase(), price);
			}
			constrainedPrices.put("min", 0L);
			constrainedPrices.put("max", 1000000000000000L);
			scopedPrices.put(provider.jsonKey(), constrainedPrices);
		}
		final List<Map<String, Object>> allScopedPrices = List.of(scopedPrices);
		details.put(FEE_SCHEDULE_FEES_KEY, allScopedPrices);
		transactionFeeSchedule.put(FEE_SCHEDULE_ENTRY_KEY, details);

		final var om = new ObjectMapper();
		return om.writerWithDefaultPrettyPrinter().writeValueAsString(transactionFeeSchedule);
	}

	private BigDecimal feeInUsd(Map<ResourceProvider, Map<UsableResource, Long>> prices, UsageAccumulator usage) {
		var sum = BigDecimal.ZERO;
		for (var provider : ResourceProvider.class.getEnumConstants()) {
			final var providerPrices = prices.get(provider);
			for (var resource : UsableResource.class.getEnumConstants()) {
				final var bdPrice = BigDecimal.valueOf(providerPrices.get(resource));
				final var bdUsage = BigDecimal.valueOf(usage.get(provider, resource));
				sum = sum.add(bdPrice.multiply(bdUsage));
			}
		}
		return sum
				.divide(FEE_SCHEDULE_MULTIPLIER, DECIMAL128)
				.divide(USD_TO_TINYCENTS, new MathContext(5, HALF_EVEN));
	}
}