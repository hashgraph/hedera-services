package com.hedera.services.throttling;

import com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.function.IntSupplier;

public class DeterministicThrottling implements TimedFunctionalityThrottling {
	private static final Logger log = LogManager.getLogger(DeterministicThrottling.class);

	private final IntSupplier capacitySplitSource;

	List<DeterministicThrottle> activeThrottles = Collections.emptyList();
	EnumMap<HederaFunctionality, ThrottleReqsManager> functionReqs = new EnumMap<>(HederaFunctionality.class);

	public DeterministicThrottling(IntSupplier capacitySplitSource) {
		this.capacitySplitSource = capacitySplitSource;
	}

	@Override
	public boolean shouldThrottle(HederaFunctionality function) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean shouldThrottle(HederaFunctionality function, Instant now) {
		ThrottleReqsManager manager;
		if ((manager = functionReqs.get(function)) == null) {
			return true;
		}
		return !manager.allReqsMetAt(now);
	}

	@Override
	public List<DeterministicThrottle> allActiveThrottles() {
		return activeThrottles;
	}

	@Override
	public List<DeterministicThrottle> activeThrottlesFor(HederaFunctionality function) {
		ThrottleReqsManager manager;
		if ((manager = functionReqs.get(function)) == null) {
			return Collections.emptyList();
		}
		return manager.managedThrottles();
	}

	@Override
	public void rebuildFor(ThrottleDefinitions defs) {
		List<DeterministicThrottle> newActiveThrottles = new ArrayList<>();
		EnumMap<HederaFunctionality, List<Pair<DeterministicThrottle, Integer>>> reqLists
				= new EnumMap<>(HederaFunctionality.class);

		int n = capacitySplitSource.getAsInt();
		for (var bucket : defs.getBuckets()) {
			var mapping = bucket.asThrottleMapping(n);
			var throttle = mapping.getLeft();
			var reqs = mapping.getRight();
			for (var req : reqs) {
				reqLists.computeIfAbsent(req.getLeft(), ignore -> new ArrayList<>())
						.add(Pair.of(throttle, req.getRight()));
			}
			newActiveThrottles.add(throttle);
		}
		EnumMap<HederaFunctionality, ThrottleReqsManager> newFunctionReqs = new EnumMap<>(HederaFunctionality.class);
		reqLists.forEach((function, reqs) -> newFunctionReqs.put(function, new ThrottleReqsManager(reqs)));

		functionReqs = newFunctionReqs;
		activeThrottles = newActiveThrottles;

		logResolvedDefinitions(log);
	}

	void logResolvedDefinitions(Logger refinedLog) {
		int n = capacitySplitSource.getAsInt();
		var sb = new StringBuilder("Resolved throttles (after splitting capacity " + n + " ways) - \n");
		functionReqs.entrySet().stream()
				.sorted(Comparator.comparing(entry -> entry.getKey().toString()))
				.forEach(entry -> {
					var function = entry.getKey();
					var manager = entry.getValue();
					sb.append("  ").append(function).append(": ")
							.append(manager.asReadableRequirements())
							.append("\n");
				});
		refinedLog.info(sb.toString().trim());
	}
}
