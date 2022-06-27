package com.hedera.services.stats;

import com.hedera.services.context.properties.NodeLocalProperties;
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

import javax.annotation.Nullable;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
	private StatEntry hapiGasStatEntry;
	@Mock
	private StatEntry consGasStatEntry;
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
	@Mock
	private NodeLocalProperties nodeProperties;

	private ThrottleUtilizations subject;

	@BeforeEach
	void setUp() {
		subject = new ThrottleUtilizations(
				handleThrottling,
				hapiThrottling,
				nodeProperties,
				runningAvg,
				halfLife);
	}

	@Test
	void initializesMetricsAsExpected() {
		givenThrottleMocksWithGas();
		givenThrottleCollabs("HAPI", List.of(aHapiStatEntry, bHapiStatEntry), hapiGasStatEntry);
		givenThrottleCollabs("cons", List.of(aConsStatEntry, bConsStatEntry), consGasStatEntry);

		subject.registerWith(platform);

		verify(platform).addAppStatEntry(aHapiStatEntry);
		verify(platform).addAppStatEntry(bHapiStatEntry);
		verify(platform).addAppStatEntry(aConsStatEntry);
		verify(platform, never()).addAppStatEntry(bConsStatEntry);
		verify(platform).addAppStatEntry(hapiGasStatEntry);
		verify(platform).addAppStatEntry(consGasStatEntry);
	}

	@Test
	void updatesAsExpectedWithGasThrottles() {
		givenThrottleMocksWithGas();
		givenThrottleCollabs("HAPI", List.of(aHapiStatEntry, bHapiStatEntry), hapiGasStatEntry);
		givenThrottleCollabs("cons", List.of(aConsStatEntry, bConsStatEntry), consGasStatEntry);
		given(hapiThrottling.gasLimitThrottle()).willReturn(hapiGasThrottle);
		given(handleThrottling.gasLimitThrottle()).willReturn(consGasThrottle);
		given(aThrottle.percentUsed(any())).willReturn(10.0);
		given(bThrottle.percentUsed(any())).willReturn(50.0);
		given(consGasThrottle.percentUsed(any())).willReturn(33.0);
		given(hapiGasThrottle.percentUsed(any())).willReturn(13.0);

		subject.registerWith(platform);
		subject.updateAll();

		verify(aThrottle, times(2)).percentUsed(any());
		verify(bThrottle, times(1)).percentUsed(any());
		verify(consGasThrottle).percentUsed(any());
		verify(hapiGasThrottle).percentUsed(any());
	}

	@Test
	void updatesAsExpectedWithNoGasThrottles() {
		givenThrottleMocksWithoutGas();
		givenThrottleCollabs("HAPI", List.of(aHapiStatEntry, bHapiStatEntry), null);
		givenThrottleCollabs("cons", List.of(aConsStatEntry, bConsStatEntry), null);
		given(aThrottle.percentUsed(any())).willReturn(10.0);
		given(bThrottle.percentUsed(any())).willReturn(50.0);

		subject.registerWith(platform);
		subject.updateAll();

		verify(aThrottle, times(2)).percentUsed(any());
		verify(bThrottle, times(1)).percentUsed(any());
	}

	@Test
	void initializesWithoutGasMetricsAsExpected() {
		givenThrottleMocksWithoutGas();
		givenThrottleCollabs("HAPI", List.of(aHapiStatEntry, bHapiStatEntry), null);
		givenThrottleCollabs("cons", List.of(aConsStatEntry, bConsStatEntry), null);

		subject.registerWith(platform);

		verify(platform).addAppStatEntry(aHapiStatEntry);
		verify(platform).addAppStatEntry(bHapiStatEntry);
		verify(platform).addAppStatEntry(aConsStatEntry);
		verify(platform, never()).addAppStatEntry(bConsStatEntry);
		verify(platform, never()).addAppStatEntry(hapiGasStatEntry);
		verify(platform, never()).addAppStatEntry(consGasStatEntry);
	}

	private void givenThrottleMocksWithGas() {
		given(nodeProperties.consThrottlesToSample()).willReturn(List.of("A", "<GAS>"));
		given(nodeProperties.hapiThrottlesToSample()).willReturn(List.of("A", "B", "<GAS>"));
		given(hapiThrottling.allActiveThrottles()).willReturn(List.of(aThrottle, bThrottle));
		given(handleThrottling.allActiveThrottles()).willReturn(List.of(aThrottle, bThrottle));
	}

	private void givenThrottleMocksWithoutGas() {
		given(nodeProperties.consThrottlesToSample()).willReturn(List.of("A"));
		given(nodeProperties.hapiThrottlesToSample()).willReturn(List.of("A", "B"));
		given(hapiThrottling.allActiveThrottles()).willReturn(List.of(aThrottle, bThrottle));
		given(handleThrottling.allActiveThrottles()).willReturn(List.of(aThrottle, bThrottle));
	}

	private void givenThrottleCollabs(
			final String prefix,
			final List<StatEntry> entries,
			@Nullable final StatEntry gasEntry
	) {
		final var names =  List.of("A", "B", "C");
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
		if (gasEntry != null) {
			given(runningAvg.from(
					eq(prefix.toLowerCase() + "GasPercentUsed"),
					eq(descFor(prefix, "Gas")),
					any(StatsRunningAverage.class))
			).willReturn(gasEntry);
		}
	}

	private String descFor(final String prefix, final String throttle) {
		return prefix + " average % used in " + throttle + " throttle bucket";
	}
}