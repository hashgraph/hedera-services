package com.hedera.services.throttling;

import com.hederahashgraph.api.proto.java.HederaFunctionality;

import java.time.Instant;

public interface TimedFunctionalityThrottling extends FunctionalityThrottling {
	default boolean shouldThrottle(HederaFunctionality function)  {
		return shouldThrottle(function, Instant.now());
	}

	boolean shouldThrottle(HederaFunctionality function, Instant now);
}
