package com.hedera.services.fees.calculation.token.txns;

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

import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class TokenUpdateResourceUsageTest {
	private TokenUpdateResourceUsage subject;

	private TransactionBody nonTokenUpdateTxn;
	private TransactionBody tokenUpdateTxn;

	@BeforeEach
	private void setup() throws Throwable {
		tokenUpdateTxn = mock(TransactionBody.class);
		given(tokenUpdateTxn.hasTokenUpdate()).willReturn(true);

		nonTokenUpdateTxn = mock(TransactionBody.class);
		given(nonTokenUpdateTxn.hasTokenUpdate()).willReturn(false);

		subject = new TokenUpdateResourceUsage();
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(tokenUpdateTxn));
		assertFalse(subject.applicableTo(nonTokenUpdateTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// expect:
		assertEquals(
				TokenUpdateResourceUsage.MOCK_TOKEN_UPDATE_USAGE,
				subject.usageGiven(null, null, null));
	}
}
