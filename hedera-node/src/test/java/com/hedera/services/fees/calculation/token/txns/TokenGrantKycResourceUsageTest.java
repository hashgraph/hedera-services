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
class TokenGrantKycResourceUsageTest {
	private TokenGrantKycResourceUsage subject;

	private TransactionBody nonTokenGrantKycTxn;
	private TransactionBody tokenGrantKycTxn;

	@BeforeEach
	private void setup() throws Throwable {
		tokenGrantKycTxn = mock(TransactionBody.class);
		given(tokenGrantKycTxn.hasTokenGrantKyc()).willReturn(true);

		nonTokenGrantKycTxn = mock(TransactionBody.class);
		given(nonTokenGrantKycTxn.hasTokenGrantKyc()).willReturn(false);

		subject = new TokenGrantKycResourceUsage();
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(tokenGrantKycTxn));
		assertFalse(subject.applicableTo(nonTokenGrantKycTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// expect:
		assertEquals(
				TokenGrantKycResourceUsage.MOCK_TOKEN_GRANT_KYC_USAGE,
				subject.usageGiven(null, null, null));
	}
}
