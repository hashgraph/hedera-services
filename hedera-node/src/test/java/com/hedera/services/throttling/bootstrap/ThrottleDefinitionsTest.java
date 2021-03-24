package com.hedera.services.throttling.bootstrap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.hedera.services.throttling.bootstrap.ThrottlesJsonToProtoSerde.loadProtoDefs;

class ThrottleDefinitionsTest {
	@Test
	void factoryWorks() {
		// given:
		var proto = loadProtoDefs("bootstrap/throttles.json");

		// expect:
		Assertions.assertEquals(proto, ThrottleDefinitions.fromProto(proto).toProto());
	}
}