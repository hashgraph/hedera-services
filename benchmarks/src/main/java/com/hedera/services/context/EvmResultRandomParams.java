package com.hedera.services.context;

public record EvmResultRandomParams(
		int maxLogs,
		int maxLogData,
		int maxCreations,
		int maxLogTopics,
		int maxOutputWords,
		int numAddressesWithChanges,
		int numStateChangesPerAddress,
		double creationProbability,
		double callSuccessProbability,
		boolean enableTraceability) {
}
