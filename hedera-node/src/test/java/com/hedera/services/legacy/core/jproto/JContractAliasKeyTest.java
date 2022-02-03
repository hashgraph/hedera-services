package com.hedera.services.legacy.core.jproto;

import org.junit.jupiter.api.Test;

import static com.swirlds.common.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.*;

class JContractAliasKeyTest {
	@Test
	void onlyValidIfAddressIsLength20() {
		final byte[] evmAddress = unhex("aaaaaaaaaaaaaaaaaaaaaaaa9abcdefabcdefbbb");
		final var empty = new JContractAliasKey(0, 0, new byte[0]);
		final var invalid = new JContractAliasKey(0, 0, "NOPE".getBytes());
		final var valid = new JContractAliasKey(0, 0, evmAddress);

		assertFalse(empty.isValid());
		assertFalse(invalid.isValid());
		assertTrue(valid.isValid());
	}
}