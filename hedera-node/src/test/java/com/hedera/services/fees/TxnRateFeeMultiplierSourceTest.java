package com.hedera.services.fees;

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.*;

import java.util.Collections;
import java.util.List;


@ExtendWith(MockitoExtension.class)
class TxnRateFeeMultiplierSourceTest {
	@Mock
	FunctionalityThrottling throttling;

	MockGlobalDynamicProps mockProps;
	TxnRateFeeMultiplierSource subject;

	@BeforeEach
	void setUp() {
		mockProps = new MockGlobalDynamicProps();
		subject = new TxnRateFeeMultiplierSource(mockProps, throttling);
	}

	@ParameterizedTest
	@CsvSource({
			"1, 10, 1, 10, 1",
			"9, 10, 1, 10, 10",
			"1, 10, 9, 10, 10",
			"89, 100, 1, 10, 1",
			"1, 10, 89, 100, 1",
			"2, 2, 1, 10, 100",
			"0, 2, 950, 1000, 25",
			"9999, 10000, 950, 1000, 100",
			"0, 1, 0, 390000, 1",
	})
	public void usesExpectedMultiplier(
			int firstUsed,
			int firstTps,
			int secondUsed,
			int secondTps,
			long expectedMultiplier
	) {
		var aThrottle = DeterministicThrottle.withTps(firstTps);
		var bThrottle = DeterministicThrottle.withTps(secondTps);
		aThrottle.allow(firstUsed);
		bThrottle.allow(secondUsed);
		given(throttling.activeThrottlesFor(CryptoTransfer)).willReturn(List.of(aThrottle, bThrottle));

		// when:
		subject.resetExpectations();
		subject.updateMultiplier();
		// and:
		long actualMultiplier = subject.currentMultiplier();

		// then:
		assertEquals(expectedMultiplier, actualMultiplier);
	}

	@Test
	void adaptsToChangedProperties() {
		var aThrottle = DeterministicThrottle.withTps(100);
		aThrottle.allow(96);
		given(throttling.activeThrottlesFor(CryptoTransfer)).willReturn(List.of(aThrottle));

		// when:
		subject.resetExpectations();
		subject.updateMultiplier();
		// then:
		Assertions.assertEquals(25, subject.currentMultiplier());
		// and when:
		mockProps.useDifferentMultipliers();
		subject.updateMultiplier();
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
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		var mockLog = mock(Logger.class);
		var desired = "Congestion pricing beginning w/ 10x multiplier";

		// when:
		subject.logMultiplierChange(1L, 10L, mockLog);

		// then:
		verify(mockLog).info(captor.capture());
		assertEquals(desired, captor.getValue());
	}

	@Test
	void logsCongestionPricingIncrease() {
		// setup:
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		var mockLog = mock(Logger.class);
		var desired = "Congestion pricing continuing, reached 100x multiplier";

		// when:
		subject.logMultiplierChange(10L, 100L, mockLog);

		// then:
		verify(mockLog).info(captor.capture());
		assertEquals(desired, captor.getValue());
	}

	@Test
	void logsCongestionPricingEnd() {
		// setup:
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		var mockLog = mock(Logger.class);
		var desired = "Congestion pricing ended";

		// when:
		subject.logMultiplierChange(10L, 1L, mockLog);

		// then:
		verify(mockLog).info(captor.capture());
		assertEquals(desired, captor.getValue());
	}

	@Test
	void silentOnCongestionPricingDrop() {
		// setup:
		var mockLog = mock(Logger.class);

		// when:
		subject.logMultiplierChange(100L, 10L, mockLog);

		// then:
		verify(mockLog, never()).info(any(String.class));
	}

	@Test
	void toStringIndicatesUnavailableConfig() {
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		var mockLog = mock(Logger.class);
		var desired = "The new cutoffs for congestion pricing are: <N/A>";

		// when:
		subject.logReadableCutoffs(mockLog);

		// then:
		verify(mockLog).info(captor.capture());
		assertEquals(desired, captor.getValue());
	}

	@Test
	void toStringHasExpectedCutoffsMsg() {
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		var mockLog = mock(Logger.class);
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
		// and:
		subject.logReadableCutoffs(mockLog);

		// then:
		verify(mockLog).info(captor.capture());
		assertEquals(desired, captor.getValue());
	}
}