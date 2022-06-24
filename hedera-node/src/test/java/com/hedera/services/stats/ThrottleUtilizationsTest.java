package com.hedera.services.stats;

import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttles.GasLimitDeterministicThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.swirlds.common.statistics.StatEntry;
import com.swirlds.common.statistics.StatsRunningAverage;
import com.swirlds.common.system.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class ThrottleUtilizationsTest {
	private static final double halfLife = 10.0;

	@Mock
	private StatEntry aHapiStatEntry;
	@Mock
	private StatEntry bHapiStatEntry;
	@Mock
	private StatEntry aConsStatEntry;
	@Mock
	private StatEntry bConsStatEntry;
	@Mock
	private Platform platform;
	@Mock
	private RunningAvgFactory runningAvg;
	@Mock
	private DeterministicThrottle aThrottle;
	@Mock
	private DeterministicThrottle bThrottle;
	@Mock
	private GasLimitDeterministicThrottle hapiGasThrottle;
	@Mock
	private GasLimitDeterministicThrottle consGasThrottle;
	@Mock
	private FunctionalityThrottling hapiThrottling;
	@Mock
	private FunctionalityThrottling handleThrottling;

	private ThrottleUtilizations subject;

	@BeforeEach
	void setUp() {
		subject = new ThrottleUtilizations(handleThrottling, hapiThrottling, runningAvg, List.of("B"), halfLife);
	}

	@Test
	void initializesMetricsAsExpected() {
		givenThrottleMocks();
		givenThrottleCollabs("HAPI", List.of(aHapiStatEntry, bHapiStatEntry));
		givenThrottleCollabs("cons", List.of(aConsStatEntry, bConsStatEntry));

		subject.registerWith(platform);

		verify(platform).addAppStatEntry(aHapiStatEntry);
		verify(platform).addAppStatEntry(bHapiStatEntry);
		verify(platform).addAppStatEntry(aConsStatEntry);
		verify(platform, never()).addAppStatEntry(bConsStatEntry);
	}

	private void givenThrottleMocks() {
		given(hapiThrottling.allActiveThrottles()).willReturn(List.of(aThrottle, bThrottle));
		given(handleThrottling.allActiveThrottles()).willReturn(List.of(aThrottle, bThrottle));
//		given(hapiThrottling.gasLimitThrottle()).willReturn(gasThrottle);
	}

	private void givenThrottleCollabs(final String prefix, final List<StatEntry> entries) {
		final var names =  List.of("A", "B");
		final var mocks =  List.of(aThrottle, bThrottle);
		for (int i = 0; i < 2; i++) {
			final var mockThrottle = mocks.get(i);
			final var mockName = names.get(i);
			final var mockEntry = entries.get(i);
			given(mockThrottle.name()).willReturn(mockName);
			final var expectedName = prefix.toLowerCase() + mockName + "PercentUsed";
			final var expectedDesc = descFor(prefix, mockName);
			if ("cons".equals(prefix) && "B".equals(mockName)) {
				continue;
			}
			given(runningAvg.from(
					eq(expectedName),
					eq(expectedDesc),
					any(StatsRunningAverage.class))
			).willReturn(mockEntry);
		}
	}

	private String descFor(final String prefix, final String throttle) {
		return prefix + " average % used in " + throttle + " throttle bucket";
	}
}