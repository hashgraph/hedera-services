package com.hedera.services.fees.calculation.crypto.txns;

/*-
 * ‌
 * Hedera Services Node
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

import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.BDDMockito.*;

class CryptoDeleteResourceUsageTest {
	private SigValueObj sigUsage;
	private CryptoFeeBuilder usageEstimator;
	private CryptoDeleteResourceUsage subject;

	private TransactionBody nonCryptoDeleteTxn;
	private TransactionBody cryptoDeleteTxn;

	@BeforeEach
	private void setup() throws Throwable {
		cryptoDeleteTxn = mock(TransactionBody.class);
		given(cryptoDeleteTxn.hasCryptoDelete()).willReturn(true);

		nonCryptoDeleteTxn = mock(TransactionBody.class);
		given(nonCryptoDeleteTxn.hasCryptoDelete()).willReturn(false);

		sigUsage = mock(SigValueObj.class);
		usageEstimator = mock(CryptoFeeBuilder.class);

		subject = new CryptoDeleteResourceUsage(usageEstimator);
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(cryptoDeleteTxn));
		assertFalse(subject.applicableTo(nonCryptoDeleteTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// when:
		subject.usageGiven(cryptoDeleteTxn, sigUsage, null);

		// then:
		verify(usageEstimator).getCryptoDeleteTxFeeMatrices(cryptoDeleteTxn, sigUsage);
	}
}
