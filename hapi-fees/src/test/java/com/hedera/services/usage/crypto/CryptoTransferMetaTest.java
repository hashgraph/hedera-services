package com.hedera.services.usage.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CryptoTransferMetaTest {
	@Test
	void setterWorks() {
		final var subject = new CryptoTransferMeta(1, 2);

		// when:
		subject.setTokenMultiplier(3);

		// then:
		assertEquals(3, subject.getTokenMultiplier());
	}
}