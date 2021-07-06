package com.hedera.services.pricing;

import org.junit.jupiter.api.Test;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanonicalOperationsTest {
	final CanonicalOperations subject = new CanonicalOperations();

	@Test
	void cryptoTransferTbd() {
		assertThrows(AssertionError.class,
				() -> subject.canonicalUsageFor(CryptoTransfer, DEFAULT));
		assertThrows(AssertionError.class,
				() -> subject.canonicalUsageFor(CryptoTransfer, TOKEN_FUNGIBLE_COMMON));
		assertThrows(AssertionError.class,
				() -> subject.canonicalUsageFor(CryptoTransfer, TOKEN_NON_FUNGIBLE_UNIQUE));
	}
}