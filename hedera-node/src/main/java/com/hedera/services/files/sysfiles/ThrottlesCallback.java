package com.hedera.services.files.sysfiles;

import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;

import java.util.function.Consumer;

public class ThrottlesCallback {
	private final FeeMultiplierSource multiplierSource;
	private final FunctionalityThrottling hapiThrottling;
	private final FunctionalityThrottling handleThrottling;

	public ThrottlesCallback(
			FeeMultiplierSource multiplierSource,
			FunctionalityThrottling hapiThrottling,
			FunctionalityThrottling handleThrottling
	) {
		this.multiplierSource = multiplierSource;
		this.hapiThrottling = hapiThrottling;
		this.handleThrottling = handleThrottling;
	}

	public Consumer<ThrottleDefinitions> throttlesCb() {
		return throttles -> {
			var defs = com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions.fromProto(throttles);
			hapiThrottling.rebuildFor(defs);
			handleThrottling.rebuildFor(defs);
			multiplierSource.resetExpectations();
		};
	}
}
