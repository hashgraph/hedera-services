package com.hedera.services.sysfiles.domain.throttling;

import com.hedera.services.sysfiles.serdes.ThrottlesJsonToProtoSerde;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ThrottleDefinitionsTest {
	@Test
	void factoryWorks() {
		// given:
		var proto = ThrottlesJsonToProtoSerde.loadProtoDefs("bootstrap/throttles.json");

		// expect:
		Assertions.assertEquals(proto, ThrottleDefinitions.fromProto(proto).toProto());
	}
}
