package com.hedera.services.fees.calculation.crypto.txns;

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

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class CryptoTransferResourceUsageTest {
	private SigValueObj sigUsage;
	private CryptoFeeBuilder usageEstimator;
	private CryptoTransferResourceUsage subject;

	private TransactionBody nonCryptoTransferTxn;
	private TransactionBody cryptoTransferTxn;

	@BeforeEach
	private void setup() throws Throwable {
		cryptoTransferTxn = mock(TransactionBody.class);
		given(cryptoTransferTxn.hasCryptoTransfer()).willReturn(true);

		nonCryptoTransferTxn = mock(TransactionBody.class);
		given(nonCryptoTransferTxn.hasCryptoTransfer()).willReturn(false);

		sigUsage = mock(SigValueObj.class);
		usageEstimator = mock(CryptoFeeBuilder.class);

		subject = new CryptoTransferResourceUsage(usageEstimator);
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(cryptoTransferTxn));
		assertFalse(subject.applicableTo(nonCryptoTransferTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// when:
		subject.usageGiven(cryptoTransferTxn, sigUsage, null);

		// then:
		verify(usageEstimator).getCryptoTransferTxFeeMatrices(cryptoTransferTxn, sigUsage);
	}
}
