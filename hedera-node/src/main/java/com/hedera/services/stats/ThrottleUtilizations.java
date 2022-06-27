package com.hedera.services.stats;

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttles.GasLimitDeterministicThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.annotations.HandleThrottle;
import com.hedera.services.throttling.annotations.HapiThrottle;
import com.swirlds.common.statistics.StatsRunningAverage;
import com.swirlds.common.system.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Singleton;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class ThrottleUtilizations {
	private static final Logger log = LogManager.getLogger(ThrottleUtilizations.class);

	private static final String GAS_THROTTLE_ID = "<GAS>";
	private static final String GAS_THROTTLE_NAME = "Gas";
	private static final String CONS_THROTTLE_PREFIX = "cons";
	private static final String HAPI_THROTTLE_PREFIX = "HAPI";

	private final double halfLife;
	private final RunningAvgFactory runningAvg;
	private final NodeLocalProperties nodeProperties;
	private final FunctionalityThrottling hapiThrottling;
	private final FunctionalityThrottling handleThrottling;
	private final Map<String, StatsRunningAverage> consNamedMetrics = new HashMap<>();
	private final Map<String, StatsRunningAverage> hapiNamedMetrics = new HashMap<>();

	public ThrottleUtilizations(
			final @HandleThrottle FunctionalityThrottling handleThrottling,
			final @HapiThrottle FunctionalityThrottling hapiThrottling,
			final NodeLocalProperties nodeProperties,
			final RunningAvgFactory runningAvg,
			final double halfLife
	) {
		this.halfLife = halfLife;
		this.runningAvg = runningAvg;
		this.hapiThrottling = hapiThrottling;
		this.handleThrottling = handleThrottling;
		this.nodeProperties = nodeProperties;
		// Need to wait to create utilizationMetrics until the throttles are initialized
	}

	public void registerWith(final Platform platform) {
		registerTypeWith(
				HAPI_THROTTLE_PREFIX,
				platform,
				nodeProperties.hapiThrottlesToSample(),
				hapiThrottling.allActiveThrottles(),
				hapiNamedMetrics);
		registerTypeWith(
				CONS_THROTTLE_PREFIX,
				platform,
				nodeProperties.consThrottlesToSample(),
				handleThrottling.allActiveThrottles(),
				consNamedMetrics);
	}

	public void updateAll() {
		final var now = Instant.now();
		update(now, handleThrottling.allActiveThrottles(), handleThrottling.gasLimitThrottle(), consNamedMetrics);
		update(now, hapiThrottling.allActiveThrottles(), hapiThrottling.gasLimitThrottle(), hapiNamedMetrics);
	}

	private void registerTypeWith(
			final String type,
			final Platform platform,
			final List<String> throttlesToSample,
			final List<DeterministicThrottle> throttles,
			final Map<String, StatsRunningAverage> namedMetrics
	) {
		throttles.stream()
				.filter(throttle -> throttlesToSample.contains(throttle.name()))
				.forEach(throttle ->
						addAndCorrelateEntryFor(type, throttle.name(), platform, namedMetrics));
		if (throttlesToSample.contains(GAS_THROTTLE_ID)) {
			addAndCorrelateEntryFor(type, GAS_THROTTLE_NAME, platform, namedMetrics);
		}
	}

	private void update(
			final Instant now,
			final List<DeterministicThrottle> activeThrottles,
			final GasLimitDeterministicThrottle gasThrottle,
			final Map<String, StatsRunningAverage> namedMetrics
	) {
		activeThrottles.forEach(throttle -> {
			final var name = throttle.name();
			if (namedMetrics.containsKey(name)) {
				namedMetrics.get(name).recordValue(throttle.percentUsed(now));
			}
		});
		if (namedMetrics.containsKey(GAS_THROTTLE_NAME)) {
			namedMetrics.get(GAS_THROTTLE_NAME).recordValue(gasThrottle.percentUsed(now));
		}
	}

	private void addAndCorrelateEntryFor(
			final String type,
			final String throttleName,
			final Platform platform,
			final Map<String, StatsRunningAverage> utilizationMetrics
	) {
		final var gauge = new StatsRunningAverage(halfLife);
		final var name = type.toLowerCase() + String.format(THROTTLE_NAME_TPL, throttleName);
		final var desc = type + String.format(THROTTLE_DESCRIPTION_TPL, throttleName);
		final var metric = runningAvg.from(name, desc, gauge);
		platform.addAppStatEntry(metric);
		utilizationMetrics.put(throttleName, gauge);
		log.info("Registered stat '{}' under name '{}'", desc, name);
	}

	private static final String THROTTLE_NAME_TPL = "%sPercentUsed";
	private static final String THROTTLE_DESCRIPTION_TPL = " average %% used in %s throttle bucket";
}
