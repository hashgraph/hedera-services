package com.hedera.services.throttling;

import com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;

public class HapiThrottling implements FunctionalityThrottling {
	private static final Logger log = LogManager.getLogger(HapiThrottling.class);

	private final TimedFunctionalityThrottling delegate;

	public HapiThrottling(TimedFunctionalityThrottling delegate) {
		this.delegate = delegate;
	}

	@Override
	public synchronized boolean shouldThrottle(HederaFunctionality function) {
		return delegate.shouldThrottle(function, Instant.now());
	}

	@Override
	public List<DeterministicThrottle> allActiveThrottles() {
		throw new UnsupportedOperationException("HAPI throttling should not be treated as a stable source of throttles!");
	}

	@Override
	public List<DeterministicThrottle> activeThrottlesFor(HederaFunctionality function) {
		throw new UnsupportedOperationException("HAPI throttling should not be treated as a stable source of throttles!");
	}

	@Override
	public void rebuildFor(ThrottleDefinitions defs) {
		delegate.rebuildFor(defs);
	}
}
