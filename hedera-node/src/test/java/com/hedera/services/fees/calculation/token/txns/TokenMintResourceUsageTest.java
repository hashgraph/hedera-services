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
class TokenMintResourceUsageTest {
	private TokenMintResourceUsage subject;

	private TransactionBody nonTokenMintTxn;
	private TransactionBody tokenMintTxn;

	@BeforeEach
	private void setup() throws Throwable {
		tokenMintTxn = mock(TransactionBody.class);
		given(tokenMintTxn.hasTokenMint()).willReturn(true);

		nonTokenMintTxn = mock(TransactionBody.class);
		given(nonTokenMintTxn.hasTokenMint()).willReturn(false);

		subject = new TokenMintResourceUsage();
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(tokenMintTxn));
		assertFalse(subject.applicableTo(nonTokenMintTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// expect:
		assertEquals(
				TokenMintResourceUsage.MOCK_TOKEN_MINT_USAGE,
				subject.usageGiven(null, null, null));
	}
}
