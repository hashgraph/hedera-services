package com.hedera.services.pricing;

/*-
 * ‌
 * Hedera Services API Fees
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
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static java.math.MathContext.DECIMAL128;
import static java.math.RoundingMode.HALF_EVEN;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FeeSchedulesTest {
	private static final double ALLOWED_DEVIATION = 0.00000001;

	private static final String FEE_SCHEDULE_FEES_KEY = "fees";
	private static final String FEE_SCHEDULE_TYPE_KEY = "subType";
	private static final String FEE_SCHEDULE_ENTRY_KEY = "transactionFeeSchedule";
	private static final String FEE_SCHEDULE_FUNCTION_KEY = "hederaFunctionality";

	private FeeSchedules subject = new FeeSchedules();
	private AssetsLoader assetsLoader = new AssetsLoader();
	private BaseOperationUsage baseOperationUsage = new BaseOperationUsage();

	@Test
	void computesExpectedPriceForUniqueTokenMint() throws IOException {
		// setup:
		final var canonicalTotalPricesInUsd = assetsLoader.loadCanonicalPrices();
		final var expectedTotalBasePrice = canonicalTotalPricesInUsd.get(TokenMint).get(TOKEN_NON_FUNGIBLE_UNIQUE);
		final var desired = "{\n" +
				"  \"transactionFeeSchedule\" : {\n" +
				"    \"hederaFunctionality\" : \"TokenMint\",\n" +
				"    \"fees\" : [ {\n" +
				"      \"subType\" : \"TOKEN_NON_FUNGIBLE_UNIQUE\",\n" +
				"      \"nodedata\" : {\n" +
				"        \"constant\" : 72128810,\n" +
				"        \"bpt\" : 115312,\n" +
				"        \"vpt\" : 288280880,\n" +
				"        \"rbh\" : 77,\n" +
				"        \"sbh\" : 6,\n" +
				"        \"gas\" : 769,\n" +
				"        \"bpr\" : 115312,\n" +
				"        \"sbpr\" : 2883,\n" +
				"        \"min\" : 0,\n" +
				"        \"max\" : 1000000000000000\n" +
				"      },\n" +
				"      \"networkdata\" : {\n" +
				"        \"constant\" : 1442576193,\n" +
				"        \"bpt\" : 2306247,\n" +
				"        \"vpt\" : 5765617594,\n" +
				"        \"rbh\" : 1537,\n" +
				"        \"sbh\" : 115,\n" +
				"        \"gas\" : 15375,\n" +
				"        \"bpr\" : 2306247,\n" +
				"        \"sbpr\" : 57656,\n" +
				"        \"min\" : 0,\n" +
				"        \"max\" : 1000000000000000\n" +
				"      },\n" +
				"      \"servicedata\" : {\n" +
				"        \"constant\" : 1442576193,\n" +
				"        \"bpt\" : 2306247,\n" +
				"        \"vpt\" : 5765617594,\n" +
				"        \"rbh\" : 1537,\n" +
				"        \"sbh\" : 115,\n" +
				"        \"gas\" : 15375,\n" +
				"        \"bpr\" : 2306247,\n" +
				"        \"sbpr\" : 57656,\n" +
				"        \"min\" : 0,\n" +
				"        \"max\" : 1000000000000000\n" +
				"      }\n" +
				"    } ]\n" +
				"  }\n" +
				"}";

		// given:
		Map<ResourceProvider, Map<UsableResource, Long>> computedResourcePrices =
				subject.canonicalPricesFor(TokenMint, TOKEN_NON_FUNGIBLE_UNIQUE);

		// and:
		final var canonicalUsage = baseOperationUsage.baseUsageFor(TokenMint, TOKEN_NON_FUNGIBLE_UNIQUE);
		final var jsonRepr = reprAsSingleFeeScheduleEntry(
				TokenMint, TOKEN_NON_FUNGIBLE_UNIQUE, computedResourcePrices);

		// when:
		final var actualBasePrice = feeInUsd(computedResourcePrices, canonicalUsage);

		// then:
		assertEquals(expectedTotalBasePrice.doubleValue(), actualBasePrice.doubleValue(), ALLOWED_DEVIATION);
		assertEquals(desired, jsonRepr);
	}

	@Test
	void computesExpectedPriceForUniqueTokenBurn() throws IOException {
		// setup:
		final var canonicalTotalPricesInUsd = assetsLoader.loadCanonicalPrices();
		final var expectedTotalBasePrice = canonicalTotalPricesInUsd.get(TokenBurn).get(TOKEN_NON_FUNGIBLE_UNIQUE);

		final var desiredJson = "{\n" +
				"  \"transactionFeeSchedule\" : {\n" +
				"    \"hederaFunctionality\" : \"TokenBurn\",\n" +
				"    \"fees\" : [ {\n" +
				"      \"subType\" : \"TOKEN_NON_FUNGIBLE_UNIQUE\",\n" +
				"      \"nodedata\" : {\n" +
				"        \"constant\" : 76366243,\n" +
				"        \"bpt\" : 122087,\n" +
				"        \"vpt\" : 305216845,\n" +
				"        \"rbh\" : 81,\n" +
				"        \"sbh\" : 6,\n" +
				"        \"gas\" : 814,\n" +
				"        \"bpr\" : 122087,\n" +
				"        \"sbpr\" : 3052,\n" +
				"        \"min\" : 0,\n" +
				"        \"max\" : 1000000000000000\n" +
				"      },\n" +
				"      \"networkdata\" : {\n" +
				"        \"constant\" : 1527324859,\n" +
				"        \"bpt\" : 2441735,\n" +
				"        \"vpt\" : 6104336894,\n" +
				"        \"rbh\" : 1628,\n" +
				"        \"sbh\" : 122,\n" +
				"        \"gas\" : 16278,\n" +
				"        \"bpr\" : 2441735,\n" +
				"        \"sbpr\" : 61043,\n" +
				"        \"min\" : 0,\n" +
				"        \"max\" : 1000000000000000\n" +
				"      },\n" +
				"      \"servicedata\" : {\n" +
				"        \"constant\" : 1527324859,\n" +
				"        \"bpt\" : 2441735,\n" +
				"        \"vpt\" : 6104336894,\n" +
				"        \"rbh\" : 1628,\n" +
				"        \"sbh\" : 122,\n" +
				"        \"gas\" : 16278,\n" +
				"        \"bpr\" : 2441735,\n" +
				"        \"sbpr\" : 61043,\n" +
				"        \"min\" : 0,\n" +
				"        \"max\" : 1000000000000000\n" +
				"      }\n" +
				"    } ]\n" +
				"  }\n" +
				"}";
		// given:
		Map<ResourceProvider, Map<UsableResource, Long>> computedResourcePrices =
				subject.canonicalPricesFor(TokenBurn, TOKEN_NON_FUNGIBLE_UNIQUE);
		// and:
		final var canonicalUsage = baseOperationUsage.baseUsageFor(TokenBurn, TOKEN_NON_FUNGIBLE_UNIQUE);
		final var jsonRepr = reprAsSingleFeeScheduleEntry(
				TokenBurn, TOKEN_NON_FUNGIBLE_UNIQUE, computedResourcePrices);
		//System.out.println(jsonRepr);

		// when:
		final var actualBasePrice = feeInUsd(computedResourcePrices, canonicalUsage);
		//System.out.println(actualBasePrice);

		// then:
		assertEquals(expectedTotalBasePrice.doubleValue(), actualBasePrice.doubleValue(), ALLOWED_DEVIATION);
		assertEquals(desiredJson, jsonRepr);
	}

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
				"        \"constant\" : 74741325,\n" +
				"        \"bpt\" : 119489,\n" +
				"        \"vpt\" : 298722453,\n" +
				"        \"rbh\" : 80,\n" +
				"        \"sbh\" : 6,\n" +
				"        \"gas\" : 797,\n" +
				"        \"bpr\" : 119489,\n" +
				"        \"sbpr\" : 2987,\n" +
				"        \"min\" : 0,\n" +
				"        \"max\" : 1000000000000000\n" +
				"      },\n" +
				"      \"networkdata\" : {\n" +
				"        \"constant\" : 1494826501,\n" +
				"        \"bpt\" : 2389780,\n" +
				"        \"vpt\" : 5974449055,\n" +
				"        \"rbh\" : 1593,\n" +
				"        \"sbh\" : 119,\n" +
				"        \"gas\" : 15932,\n" +
				"        \"bpr\" : 2389780,\n" +
				"        \"sbpr\" : 59744,\n" +
				"        \"min\" : 0,\n" +
				"        \"max\" : 1000000000000000\n" +
				"      },\n" +
				"      \"servicedata\" : {\n" +
				"        \"constant\" : 1494826501,\n" +
				"        \"bpt\" : 2389780,\n" +
				"        \"vpt\" : 5974449055,\n" +
				"        \"rbh\" : 1593,\n" +
				"        \"sbh\" : 119,\n" +
				"        \"gas\" : 15932,\n" +
				"        \"bpr\" : 2389780,\n" +
				"        \"sbpr\" : 59744,\n" +
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
		final var canonicalUsage = baseOperationUsage.baseUsageFor(TokenFeeScheduleUpdate, DEFAULT);
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
		final var canonicalUsage = baseOperationUsage.baseUsageFor(ConsensusSubmitMessage, DEFAULT);

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
