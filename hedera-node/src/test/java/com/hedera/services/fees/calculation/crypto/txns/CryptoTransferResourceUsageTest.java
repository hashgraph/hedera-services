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

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.crypto.CryptoTransferUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.*;
import static org.mockito.BDDMockito.mock;

class CryptoTransferResourceUsageTest {
	private CryptoTransferResourceUsage subject;

	private TransactionBody nonCryptoTransferTxn;
	private TransactionBody cryptoTransferTxn;

	StateView view;
	int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
	SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
	SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);
	FeeData expected;

	CryptoTransferUsage usage;
	GlobalDynamicProperties props = new MockGlobalDynamicProps();
	BiFunction<TransactionBody, SigUsage, CryptoTransferUsage> factory;

	@BeforeEach
	private void setup() throws Throwable {
		view = mock(StateView.class);
		expected = mock(FeeData.class);

		cryptoTransferTxn = mock(TransactionBody.class);
		given(cryptoTransferTxn.hasCryptoTransfer()).willReturn(true);

		nonCryptoTransferTxn = mock(TransactionBody.class);
		given(nonCryptoTransferTxn.hasCryptoTransfer()).willReturn(false);

		factory = (BiFunction<TransactionBody, SigUsage, CryptoTransferUsage>)mock(BiFunction.class);
		given(factory.apply(cryptoTransferTxn, sigUsage)).willReturn(usage);

		usage = mock(CryptoTransferUsage.class);
		given(usage.givenTokenMultiplier(anyInt())).willReturn(usage);
		given(usage.get()).willReturn(expected);

		CryptoTransferResourceUsage.factory = factory;
		given(factory.apply(cryptoTransferTxn, sigUsage)).willReturn(usage);

		subject = new CryptoTransferResourceUsage(props);
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(cryptoTransferTxn));
		assertFalse(subject.applicableTo(nonCryptoTransferTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// expect:
		assertEquals(
				expected,
				subject.usageGiven(cryptoTransferTxn, obj, view));
		// and:
		verify(usage).givenTokenMultiplier(props.feesTokenTransferUsageMultiplier());
	}
}
