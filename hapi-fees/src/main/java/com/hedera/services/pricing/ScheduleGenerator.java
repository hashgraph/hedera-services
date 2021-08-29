package com.hedera.services.pricing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;

public class ScheduleGenerator {
	private static final String FEE_SCHEDULE_FEES_KEY = "fees";
	private static final String FEE_SCHEDULE_TYPE_KEY = "subType";
	private static final String FEE_SCHEDULE_ENTRY_KEY = "transactionFeeSchedule";
	private static final String FEE_SCHEDULE_FUNCTION_KEY = "hederaFunctionality";

	public static void main(String... args) {

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

	@SuppressWarnings("unchecked")
	private static final List<Pair<HederaFunctionality, List<SubType>>> SUPPORTED_FUNCTIONS = List.of(new Pair[] {
					/* Crypto */
					Pair.of(CryptoTransfer, List.of(
							DEFAULT,
							TOKEN_FUNGIBLE_COMMON,
							TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES,
							TOKEN_NON_FUNGIBLE_UNIQUE,
							TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES
					)),
					/* Token */
					Pair.of(TokenCreate, List.of(
							TOKEN_FUNGIBLE_COMMON,
							TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES,
							TOKEN_NON_FUNGIBLE_UNIQUE,
							TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES
					)),
					Pair.of(TokenMint, List.of(
							TOKEN_NON_FUNGIBLE_UNIQUE
					)),
					Pair.of(TokenBurn, List.of(
							TOKEN_NON_FUNGIBLE_UNIQUE
					)),
					Pair.of(TokenAccountWipe, List.of(
							TOKEN_NON_FUNGIBLE_UNIQUE
					)),
					Pair.of(TokenFeeScheduleUpdate, List.of(
							DEFAULT
					)),
					/* Consensus */
					Pair.of(ConsensusSubmitMessage, List.of(
							DEFAULT
					)),
			}
	);

}
