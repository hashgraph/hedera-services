package com.hedera.services.sysfiles.domain.throttling;

import com.hedera.services.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class ThrottleDefinitionsTest {
	@Test
	void factoryWorks() throws IOException {
		// given:
		var proto = TestUtils.protoDefs("bootstrap/throttles.json");

		// expect:
		Assertions.assertEquals(proto, ThrottleDefinitions.fromProto(proto).toProto());
	}
}
