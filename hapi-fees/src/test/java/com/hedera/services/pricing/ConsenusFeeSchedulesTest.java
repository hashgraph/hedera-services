package com.hedera.services.pricing;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;

public class ConsenusFeeSchedulesTest extends FeeSchedulesTestHelper {
	@Test
	void computesExpectedPriceForSubmitMessageSubyptes() throws IOException {
		testExpectedPriceFor(ConsensusSubmitMessage, DEFAULT);
	}
}
