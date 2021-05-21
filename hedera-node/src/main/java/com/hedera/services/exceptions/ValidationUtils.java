package com.hedera.services.exceptions;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

public class ValidationUtils {
	public static void validateTrue(boolean flag, ResponseCodeEnum code) {
		if (!flag) {
			throw new InvalidTransactionException(code);
		}
	}

	public static void validateFalse(boolean flag, ResponseCodeEnum code) {
		if (flag) {
			throw new InvalidTransactionException(code);
		}
	}

	public static void checkInvariant(boolean flag, Supplier<String> failureMsg) {
		if (!flag) {
			throw new InvalidTransactionException(failureMsg.get(), FAIL_INVALID);
		}
	}
}
