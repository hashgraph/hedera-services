package com.hedera.services.usage.token;

import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenOpsUsageTest {
	private final TokenOpsUsage subject = new TokenOpsUsage();

	@Test
	void knowsBytesNeededToReprCustomFeeSchedule() {
		// setup:
		final var expectedHbarFixed = FeeBuilder.LONG_SIZE + FeeBuilder.BASIC_ENTITY_ID_SIZE;
		final var expectedHtsFixed = FeeBuilder.LONG_SIZE + 2 * FeeBuilder.BASIC_ENTITY_ID_SIZE;
		final var expectedFractional = 4 * FeeBuilder.LONG_SIZE;
		// given:
		final var perHbarFixedFee = subject.bytesNeededToRepr(1, 0, 0);
		final var perHtsFixedFee = subject.bytesNeededToRepr(0, 1, 0);
		final var perFracFee = subject.bytesNeededToRepr(0, 0, 1);
		final var oneOfEach = subject.bytesNeededToRepr(1, 1, 1);

		// expect:
		assertEquals(expectedHbarFixed, perHbarFixedFee);
		assertEquals(expectedHtsFixed, perHtsFixedFee);
		assertEquals(expectedFractional, perFracFee);
		assertEquals(expectedHbarFixed + expectedHtsFixed + expectedFractional, oneOfEach);
	}

	@Test
	void accumulatesBptAndRbhAsExpected() {
		// setup:
		final var now = 1_234_567L;
		final var expiry = now + 7776000L;
		final var curSize = subject.bytesNeededToRepr(1, 0 ,1);
		final var ctx = new ExtantFeeScheduleContext(expiry, curSize);
	}

}