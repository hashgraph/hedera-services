package com.hedera.services.sysfiles.validation;

/*-
 * ‌
 * Hedera Services API Utilities
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

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
		assertDoesNotThrow(ErrorCodeUtils::new);

		// given:
		var msg = "NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION :: YIKES!";

		// expect:
		assertEquals(Optional.of(NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION), ErrorCodeUtils.errorFrom(msg));
	}

	@Test
	void returnsEmptyOptionalIfNoErrorCode() {
		// given:
		var msg = "YIKES!";

		// expect:
		assertEquals(Optional.empty(), ErrorCodeUtils.errorFrom(msg));
	}
}
