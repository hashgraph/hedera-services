package com.hedera.services.fees;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static java.time.Instant.ofEpochSecond;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class TxnRateFeeMultiplierSourceTest {
	Instant[] congestionStarts = new Instant[] { ofEpochSecond(1L), ofEpochSecond(2L), ofEpochSecond(3L), };
	Instant consensusNow = congestionStarts[2].plusSeconds(1L);

	@Mock
	FunctionalityThrottling throttling;

	MockGlobalDynamicProps mockProps;

	@Inject
	private LogCaptor logCaptor;
	@LoggingSubject
	private TxnRateFeeMultiplierSource subject;

	@BeforeEach
	void setUp() {
		mockProps = new MockGlobalDynamicProps();
		subject = new TxnRateFeeMultiplierSource(mockProps, throttling);
	}

	@Test
	void updatesCongestionStarts() {
		// when:
		subject.resetCongestionLevelStarts(congestionStarts);

		// then:
		Assertions.assertSame(congestionStarts, subject.congestionLevelStarts());
	}

	/* MockGlobalDynamicProps has 2 secs for minCongestionPeriod */
	@ParameterizedTest
	@CsvSource({
			"9, 1, 2, 3, 1, 10, 1, 10, 1, -1, -1, -1",
			"9, -1, -1, -1, 9, 10, 1, 10, 1, 9, -1, -1",
			"9, -1, -1, -1, 1, 10, 9, 10, 1, 9, -1, -1",
			"9, 3, 3, 3, 89, 100, 1, 10, 1, -1, -1, -1",
			"9, 1, -1, -1, 1, 10, 89, 100, 1, -1, -1, -1",
			"9, 1, 2, -1, 2, 2, 1, 10, 25, 1, 2, 9",
			"9, 3, 3, 3, 0, 2, 950, 1000, 25, 3, 3, -1",
			"9, -1, -1, -1, 9999, 10000, 950, 1000, 1, 9, 9, 9",
			"9, -1, -1, -1, 0, 1, 0, 390000, 1, -1, -1, -1",
			"4, -1, -1, -1, 100, 100, 1000, 1000, 1, 4, 4, 4",
			"5, 3, -1, -1, 100, 100, 1000, 1000, 10, 3, 5, 5",
			"5, 1, 3, 4, 100, 100, 1000, 1000, 25, 1, 3, 4",
	})
	public void usesExpectedMultiplier(
			long consensusSec,
			long old10XLevelStart,
			long old25XLevelStart,
			long old100XLevelStart,
			int firstUsed,
			int firstTps,
			int secondUsed,
			int secondTps,
			long expectedMultiplier,
			long new10XLevelStart,
			long new25XLevelStart,
			long new100XLevelStart
	) {
		var aThrottle = DeterministicThrottle.withTps(firstTps);
		var bThrottle = DeterministicThrottle.withTps(secondTps);
		aThrottle.allow(firstUsed);
		bThrottle.allow(secondUsed);
		given(throttling.activeThrottlesFor(CryptoTransfer)).willReturn(List.of(aThrottle, bThrottle));

		// when:
		subject.resetExpectations();
		subject.resetCongestionLevelStarts(instants(old10XLevelStart, old25XLevelStart, old100XLevelStart));
		subject.updateMultiplier(Instant.ofEpochSecond(consensusSec));
		// and:
		long actualMultiplier = subject.currentMultiplier();

		// then:
		assertEquals(expectedMultiplier, actualMultiplier);
		// and:
		var starts = subject.congestionLevelStarts();
		if (new10XLevelStart == -1) {
			assertNull(starts[0]);
		} else {
			assertEquals(new10XLevelStart, starts[0].getEpochSecond());
		}
		if (new25XLevelStart == -1) {
			assertNull(starts[1]);
		} else {
			assertEquals(new25XLevelStart, starts[1].getEpochSecond());
		}
		if (new100XLevelStart == -1) {
			assertNull(starts[2]);
		} else {
			assertEquals(new100XLevelStart, starts[2].getEpochSecond());
		}
	}

	@Test
	void adaptsToChangedProperties() {
		var aThrottle = DeterministicThrottle.withTps(100);
		aThrottle.allow(96);
		given(throttling.activeThrottlesFor(CryptoTransfer)).willReturn(List.of(aThrottle));

		// when:
		subject.resetExpectations();
		subject.resetCongestionLevelStarts(instants(1L, 1L, 1L));
		subject.updateMultiplier(consensusNow);
		// then:
		Assertions.assertEquals(25, subject.currentMultiplier());
		// and when:
		mockProps.useDifferentMultipliers();
		subject.updateMultiplier(consensusNow);
		// then:
		Assertions.assertEquals(26, subject.currentMultiplier());
	}

	@Test
	void doesntThrowOnMissingThrottles() {
		given(throttling.activeThrottlesFor(CryptoTransfer)).willReturn(Collections.emptyList());

		// expect:
		Assertions.assertDoesNotThrow(subject::resetExpectations);
		assertEquals(1L, subject.currentMultiplier());
	}

	@Test
	void logsCongestionPricingStart() {
		// setup:
		var desired = "Congestion pricing beginning w/ 10x multiplier";

		// when:
		subject.logMultiplierChange(1L, 10L);

		// then:
		assertThat(logCaptor.infoLogs(), contains(desired));
	}

	@Test
	void logsCongestionPricingIncrease() {
		// given:
		var desired = "Congestion pricing continuing, reached 100x multiplier";

		// when:
		subject.logMultiplierChange(10L, 100L);

		// then:
		assertThat(logCaptor.infoLogs(), contains(desired));
	}

	@Test
	void logsCongestionPricingEnd() {
		// setup:
		var desired = "Congestion pricing ended";

		// when:
		subject.logMultiplierChange(10L, 1L);

		// then:
		assertThat(logCaptor.infoLogs(), contains(desired));
	}

	@Test
	void silentOnCongestionPricingDrop() {
		// when:
		subject.logMultiplierChange(100L, 10L);

		// then:
		Assertions.assertTrue(logCaptor.infoLogs().isEmpty());
	}

	@Test
	void toStringIndicatesUnavailableConfig() {
		var desired = "The new cutoffs for congestion pricing are: <N/A>";

		// when:
		subject.logReadableCutoffs();

		// then:
		assertThat(logCaptor.infoLogs(), contains(desired));
	}

	@Test
	void toStringHasExpectedCutoffsMsg() {
		var desired = "The new cutoffs for congestion pricing are:\n" +
				"  (A) When logical TPS exceeds:\n" +
				"    900.00 TPS, multiplier is 10x\n" +
				"    950.00 TPS, multiplier is 25x\n" +
				"    990.00 TPS, multiplier is 100x\n" +
				"  (B) When logical TPS exceeds:\n" +
				"    9.00 TPS, multiplier is 10x\n" +
				"    9.50 TPS, multiplier is 25x\n" +
				"    9.90 TPS, multiplier is 100x";

		var aThrottle = DeterministicThrottle.withTpsNamed(1000, "A");
		var bThrottle = DeterministicThrottle.withTpsNamed(10, "B");
		given(throttling.activeThrottlesFor(CryptoTransfer)).willReturn(List.of(aThrottle, bThrottle));

		// when:
		subject.resetExpectations();

		// then:
		assertThat(logCaptor.infoLogs(), contains(desired));
	}

	private Instant[] instants(long a, long b, long c) {
		var ans = new Instant[3];
		ans[0] = (a == -1) ? null : Instant.ofEpochSecond(a);
		ans[1] = (b == -1) ? null : Instant.ofEpochSecond(b);
		ans[2] = (c == -1) ? null : Instant.ofEpochSecond(c);
		return ans;
	}
}
