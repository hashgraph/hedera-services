package com.hedera.services.legacy.unit.utils;

import com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hederahashgraph.api.proto.java.HederaFunctionality;

import java.util.Collections;
import java.util.List;

public class DummyFunctionalityThrottling {
	public static FunctionalityThrottling throttlingAlways(boolean shouldThrottle) {
		return new FunctionalityThrottling() {
			@Override
			public boolean shouldThrottle(HederaFunctionality function) {
				return shouldThrottle;
			}

			@Override
			public void rebuildFor(ThrottleDefinitions defs) {
			}

			@Override
			public List<DeterministicThrottle> activeThrottlesFor(HederaFunctionality function) {
				return Collections.emptyList();
			}

			@Override
			public List<DeterministicThrottle> allActiveThrottles() {
				return Collections.emptyList();
			}
		};
	}
}
