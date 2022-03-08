package com.hedera.services.submerkle;

public record EvmResultRandomParams(
		int maxLogs,
		int maxLogData,
		int maxCreations,
		int maxLogTopics,
		int maxOutputWords,
		int numAddressesWithChanges,
		int numStateChangesPerAddress,
		double creationProbability,
		boolean enableTraceability) {
}
