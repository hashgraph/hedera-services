package com.hedera.services.exceptions;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvalidTransactionExceptionTest {
	@Test
	void canBuildRevertingExceptionWithDetail() {
		final var reason = "I don't like it!";
		final var frameReason = Bytes.of(reason.getBytes());
		final var revertingEx = InvalidTransactionException.fromReverting(
				INVALID_ALLOWANCE_OWNER_ID, reason);

		assertTrue(revertingEx.isReverting());
		assertEquals(frameReason, revertingEx.getRevertReason());
	}

	@Test
	void canBuildRevertingExceptionNoDetail() {
		final var frameReason = Bytes.of(INVALID_ALLOWANCE_OWNER_ID.name().getBytes());
		final var revertingEx = InvalidTransactionException.fromReverting(
				INVALID_ALLOWANCE_OWNER_ID);

		assertTrue(revertingEx.isReverting());
		assertEquals(frameReason, revertingEx.getRevertReason());
	}

	@Test
	void mostExceptionsArentReverting() {
		final var otherEx = new InvalidTransactionException(INVALID_TRANSACTION_BODY);

		assertFalse(otherEx.isReverting());
		assertThrows(IllegalStateException.class, otherEx::getRevertReason);
	}
}