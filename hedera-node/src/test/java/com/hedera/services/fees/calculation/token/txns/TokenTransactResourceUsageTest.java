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
class TokenTransactResourceUsageTest {

	private TokenTransactResourceUsage subject;

	private TransactionBody nonTokenTransactTxn;
	private TransactionBody tokenTransactTxn;

	@BeforeEach
	private void setup() throws Throwable {
		tokenTransactTxn = mock(TransactionBody.class);
		given(tokenTransactTxn.hasTokenTransfers()).willReturn(true);

		nonTokenTransactTxn = mock(TransactionBody.class);
		given(nonTokenTransactTxn.hasTokenTransfers()).willReturn(false);

		subject = new TokenTransactResourceUsage();
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(tokenTransactTxn));
		assertFalse(subject.applicableTo(nonTokenTransactTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// expect:
		assertEquals(
				TokenTransactResourceUsage.MOCK_TOKEN_TRANSACT_USAGE,
				subject.usageGiven(null, null, null));
	}
}