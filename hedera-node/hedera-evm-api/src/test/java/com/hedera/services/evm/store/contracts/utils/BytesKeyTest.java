package com.hedera.services.evm.store.contracts.utils;

import com.swirlds.common.utility.CommonUtils;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BytesKeyTest {
	@Test
	void toStringIncorporatesArrayContents() {
		final var literal = Hex.decode("abcdef");
		final var desired = "BytesKey[array=" + CommonUtils.hex(literal) + "]";

		final var subject = new BytesKey(literal);

		assertEquals(desired, subject.toString());
	}
}