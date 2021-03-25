package com.hedera.services.throttling;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION;
import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeUtilsTest {
	@Test
	void usesTplForExceptionMsg() {
		// setup:
		var details = "YIKES!";
		var expectedMsg = "NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION :: " + details;

		// when:
		var actualMsg = ErrorCodeUtils.exceptionMsgFor(NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION, details);

		//
		assertEquals(expectedMsg, actualMsg);
	}

	@Test
	void extractsErrorCodeFromMsg() {
		// given:
		var msg = "NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION :: YIKES!";

		// expect:
		assertEquals(Optional.of(NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION), ErrorCodeUtils.errorFrom(msg));
	}

	@Test
	void returnsEmptyOptionalIfNoErrorCode() {
		// given:
		var msg = "NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION :: YIKES!";

		// expect:
		assertEquals(NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION, ErrorCodeUtils.errorFrom(msg));
	}
}