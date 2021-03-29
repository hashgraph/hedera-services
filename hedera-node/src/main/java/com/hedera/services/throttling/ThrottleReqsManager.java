package com.hedera.services.throttling;

import com.hedera.services.throttles.DeterministicThrottle;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ThrottleReqsManager {
	private final boolean[] passedReq;
	private final List<Pair<DeterministicThrottle, Integer>> allReqs;

	public ThrottleReqsManager(List<Pair<DeterministicThrottle, Integer>> allReqs) {
		this.allReqs = allReqs;
		passedReq = new boolean[allReqs.size()];
	}

	public boolean allReqsMetAt(Instant now) {
		var allPassed = true;
		for (int i = 0; i < passedReq.length; i++) {
			var req = allReqs.get(i);
			passedReq[i] = req.getLeft().allow(req.getRight(), now);
			allPassed &= passedReq[i];
		}

		if (!allPassed) {
			for (int i = 0; i < passedReq.length; i++) {
				if (passedReq[i]) {
					allReqs.get(i).getLeft().reclaimLastAllowedUse();
				}
			}
		}

		return allPassed;
	}

	public List<DeterministicThrottle.UsageSnapshot> currentUsage() {
		List<DeterministicThrottle.UsageSnapshot> usages = new ArrayList<>();
		for (var req : allReqs) {
			usages.add(req.getLeft().usageSnapshot());
		}
		return usages;
	}

	List<DeterministicThrottle> managedThrottles() {
		return allReqs.stream().map(Pair::getLeft).collect(Collectors.toList());
	}

	String asReadableRequirements() {
		return "min{" + allReqs.stream().map(this::readable).collect(Collectors.joining(", ")) + "}";
	}

	private String readable(Pair<DeterministicThrottle, Integer> req) {
		var throttle = req.getLeft();
		return approximateTps(req.getRight(), throttle.mtps()) + " tps (" + throttle.name() + ")";
	}

	private String approximateTps(int logicalTpsReq, long bucketMtps) {
		return String.format("%.2f", (1.0 * bucketMtps) / 1000.0 / logicalTpsReq);
	}
}
