package com.hedera.services.stats;

import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.annotations.HandleThrottle;
import com.hedera.services.throttling.annotations.HapiThrottle;
import com.swirlds.common.statistics.StatsRunningAverage;
import com.swirlds.common.system.Platform;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class ThrottleUtilizations {
	private final double halfLife;
	private final List<String> queryOnlyBuckets;
	private final RunningAvgFactory runningAvg;
	private final FunctionalityThrottling hapiThrottling;
	private final FunctionalityThrottling handleThrottling;
	private final List<Pair<String, StatsRunningAverage>> hapiNamedMetrics = new ArrayList<>();
	private final List<Pair<String, StatsRunningAverage>> consNamedMetrics = new ArrayList<>();

	public ThrottleUtilizations(
			final @HandleThrottle FunctionalityThrottling handleThrottling,
			final @HapiThrottle FunctionalityThrottling hapiThrottling,
			final RunningAvgFactory runningAvg,
			final List<String> queryOnlyBuckets,
			final double halfLife
	) {
		this.halfLife = halfLife;
		this.runningAvg = runningAvg;
		this.hapiThrottling = hapiThrottling;
		this.handleThrottling = handleThrottling;
		this.queryOnlyBuckets = queryOnlyBuckets;
		// Need to wait to create utilizationMetrics until the throttles are initialized
	}

	public void registerWith(final Platform platform) {
		hapiThrottling.allActiveThrottles().forEach(throttle ->
				addAndCorrelateEntryFor("HAPI", throttle.name(), platform, hapiNamedMetrics));
		handleThrottling.allActiveThrottles().stream()
				.filter(throttle -> !queryOnlyBuckets.contains(throttle.name()))
				.forEach(throttle ->
						addAndCorrelateEntryFor("cons", throttle.name(), platform, consNamedMetrics));
	}

	private void addAndCorrelateEntryFor(
			final String type,
			final String throttleName,
			final Platform platform,
			final List<Pair<String, StatsRunningAverage>> utilizationMetrics
	) {
		final var gauge = new StatsRunningAverage(halfLife);
		final var metric = runningAvg.from(
				type.toLowerCase() + String.format(THROTTLE_NAME_TPL, throttleName),
				type + String.format(THROTTLE_DESCRIPTION_TPL, throttleName),
				gauge);
		platform.addAppStatEntry(metric);
		utilizationMetrics.add(Pair.of(throttleName, gauge));
	}

	private static final String THROTTLE_NAME_TPL = "%sPercentUsed";
	private static final String THROTTLE_DESCRIPTION_TPL = " average %% used in %s throttle bucket";
}
