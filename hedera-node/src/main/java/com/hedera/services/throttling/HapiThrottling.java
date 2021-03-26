package com.hedera.services.throttling;

import com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.AddressBook;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class HapiThrottling implements FunctionalityThrottling {
	private static final Logger log = LogManager.getLogger(HapiThrottling.class);

	private final Supplier<AddressBook> book;

	public HapiThrottling(Supplier<AddressBook> book) {
		this.book = book;
	}

	@Override
	public boolean shouldThrottle(HederaFunctionality function) {
		throw new AssertionError("Not implemented!");
	}

	@Override
	public List<DeterministicThrottle> allActiveThrottles() {
		throw new AssertionError("Not implemented!");
	}

	@Override
	public List<DeterministicThrottle> activeThrottlesFor(HederaFunctionality function) {
		return Collections.emptyList();
	}

	@Override
	public void rebuildFor(ThrottleDefinitions defs) {
		log.info("Lulz I have no idea what to do with whatever this is");
	}
}
