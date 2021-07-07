package com.hedera.services.usage.token.meta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeeScheduleUpdateMetaTest {
	@Test
	void toStringWorks() {
		// given:
		final var desired = "FeeScheduleUpdateMeta{effConsensusTime=1234567, " +
				"numBytesInNewFeeScheduleRepr=22, numBytesInGrpcFeeScheduleRepr=33}";

		// when:
		final var subject = new FeeScheduleUpdateMeta(1_234_567L, 22, 33);

		// then:
		assertEquals(desired, subject.toString());
	}
}