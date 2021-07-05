package com.hedera.services.pricing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;

class FeeSchedulesTest {
	private final FeeSchedules subject = new FeeSchedules();

	@Test
	void computesExpectedPriceForSubmitMessage() throws IOException {
		AtomicReference<Map<ResourceProvider, Map<UsableResource, Long>>> ans = new AtomicReference<>();

		Assertions.assertDoesNotThrow(() -> ans.set(subject.canonicalPricesFor(ConsensusSubmitMessage, DEFAULT)));

		System.out.println(ans.get());
	}
}