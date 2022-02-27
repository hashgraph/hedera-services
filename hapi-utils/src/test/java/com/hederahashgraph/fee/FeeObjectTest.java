package com.hederahashgraph.fee;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FeeObjectTest {
	@Test
	void toStringWorks() {
		final var subject = new FeeObject(1L, 2L, 3L);
		final var desired = "FeeObject{nodeFee=1, networkFee=2, serviceFee=3}";

		Assertions.assertEquals(desired, subject.toString());
	}
}