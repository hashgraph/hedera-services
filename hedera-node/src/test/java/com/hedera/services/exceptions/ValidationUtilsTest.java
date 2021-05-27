package com.hedera.services.exceptions;

import org.junit.jupiter.api.Test;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidationUtilsTest {
	@Test
	void factoriesWorkAsExpected() {
		// when:
		final var falseExCapturedByCode = assertThrows(InvalidTransactionException.class, () ->
				validateTrue(false, MEMO_TOO_LONG));
		final var falseExCapturedByCodeAndMsg = assertThrows(InvalidTransactionException.class, () ->
				validateTrue(false, INVALID_TOKEN_BURN_AMOUNT, () -> "Should be true!"));
		final var trueExCapturedByCode = assertThrows(InvalidTransactionException.class, () ->
				validateFalse(true, CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT));
		final var trueExCapturedByCodeAndMsg = assertThrows(InvalidTransactionException.class, () ->
				validateFalse(true, TOKEN_HAS_NO_SUPPLY_KEY, () -> "Should be false!"));

		// then:
		assertEquals(MEMO_TOO_LONG, falseExCapturedByCode.getResponseCode());
		assertEquals(INVALID_TOKEN_BURN_AMOUNT, falseExCapturedByCodeAndMsg.getResponseCode());
		assertEquals("Should be true!", falseExCapturedByCodeAndMsg.getMessage());
		assertEquals(CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT, trueExCapturedByCode.getResponseCode());
		assertEquals(TOKEN_HAS_NO_SUPPLY_KEY, trueExCapturedByCodeAndMsg.getResponseCode());
		assertEquals("Should be false!", trueExCapturedByCodeAndMsg.getMessage());
	}
}