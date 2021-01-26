package com.hedera.services.txns.network;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.UncheckedSubmitBody;
import org.junit.jupiter.api.Test;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UncheckedSubmitTransitionLogicTest {
	UncheckedSubmitTransitionLogic subject = new UncheckedSubmitTransitionLogic();

	@Test
	public void hasExpectedApplicability() {
		// setup:
		var applicability = subject.applicability();

		// expect:
		assertTrue(applicability.test(
				TransactionBody.newBuilder()
						.setUncheckedSubmit(UncheckedSubmitBody.getDefaultInstance()).build()));
		assertFalse(applicability.test(
				TransactionBody.newBuilder()
						.setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance()).build()));
	}

	@Test
	public void rubberstampsEverything() {
		// expect:
		assertEquals(OK, subject.syntaxCheck().apply(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void throwsIseOnTransitionAttempt() {
		// expect:
		assertDoesNotThrow(subject::doStateTransition);
	}
}