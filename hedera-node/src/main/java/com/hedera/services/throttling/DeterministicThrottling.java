package com.hedera.services.throttling;

import com.hedera.services.files.interceptors.TxnAwareRatesManager;
import com.hedera.services.throttling.bootstrap.ThrottleDefinitions;
import com.hedera.services.throttling.real.DeterministicThrottle;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.function.IntSupplier;

public class DeterministicThrottling implements FunctionalityThrottling {
	private static final Logger log = LogManager.getLogger(DeterministicThrottling.class);

	private final IntSupplier capacitySplitSource;

	private EnumMap<HederaFunctionality, List<Pair<DeterministicThrottle, Integer>>>
			functionReqs = new EnumMap<>(HederaFunctionality.class);

	public DeterministicThrottling(IntSupplier capacitySplitSource) {
		this.capacitySplitSource = capacitySplitSource;
	}

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
		log.info("Like I would do that for you.");
		return Collections.emptyList();
	}

	@Override
	public void rebuildFor(ThrottleDefinitions defs) {
		log.info("Lulz I have no idea what to do with these definitions {}", defs.toProto());
	}
}
