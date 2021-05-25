package com.hedera.services.legacy.proto.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SignatureGeneratorTest {
	@Test
	void rejectsNonEddsaKeys() {
		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> SignatureGenerator.signBytes(new byte[0], null));
	}
}