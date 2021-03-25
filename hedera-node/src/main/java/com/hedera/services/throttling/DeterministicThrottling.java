package com.hedera.services.throttling;

import com.hedera.services.throttling.real.DeterministicThrottle;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.commons.lang3.tuple.Pair;

import java.util.EnumMap;
import java.util.List;

public class DeterministicThrottling implements FunctionalityThrottling {
	private EnumMap<HederaFunctionality, List<Pair<DeterministicThrottle, Integer>>> functionReqs =
			new EnumMap<>(HederaFunctionality.class);

	@Override
	public boolean shouldThrottle(HederaFunctionality function) {
		throw new AssertionError("Not implemented!");
	}

	@Override
	public List<DeterministicThrottle.UsageSnapshot> currentUsageFor(HederaFunctionality function) {
		throw new AssertionError("Not implemented!");
	}

	@Override
	public List<DeterministicThrottle> allActiveThrottles() {
		throw new AssertionError("Not implemented!");
	}
}
