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

import com.google.protobuf.ByteString;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.context.primitives.StateView;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import com.hederahashgraph.fee.SigValueObj;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.mockito.BDDMockito.*;
import static com.hedera.test.utils.IdUtils.*;

class CryptoUpdateResourceUsageTest {
	Key currKey = Key.newBuilder().setEd25519(ByteString.copyFrom("NONSENSE".getBytes())).build();
	MerkleEntityId accountKey = new MerkleEntityId(0, 0, 1234);
	AccountID target = asAccount("0.0.1234");
	Timestamp expiry = Timestamp.newBuilder().setSeconds(Long.MAX_VALUE).build();
	StateView view;
	MerkleAccount account;
	FCMap<MerkleEntityId, MerkleAccount> accounts;

	private SigValueObj sigUsage;
	private CryptoFeeBuilder usageEstimator;
	private CryptoUpdateResourceUsage subject;

	private TransactionBody nonCryptoUpdateTxn;
	private TransactionBody cryptoUpdateTxn;

	@BeforeEach
	private void setup() throws Exception {
		cryptoUpdateTxn = mock(TransactionBody.class);
		CryptoUpdateTransactionBody update = mock(CryptoUpdateTransactionBody.class);
		given(update.getAccountIDToUpdate()).willReturn(target);
		given(cryptoUpdateTxn.hasCryptoUpdateAccount()).willReturn(true);
		given(cryptoUpdateTxn.getCryptoUpdateAccount()).willReturn(update);

		nonCryptoUpdateTxn = mock(TransactionBody.class);
		given(nonCryptoUpdateTxn.hasCryptoUpdateAccount()).willReturn(false);

		account = mock(MerkleAccount.class);
		given(account.getKey()).willReturn(JKey.mapKey(currKey));
		given(account.getExpiry()).willReturn(Long.MAX_VALUE);
		accounts = mock(FCMap.class);
		given(accounts.get(accountKey)).willReturn(account);
		view = mock(StateView.class);
		given(view.accounts()).willReturn(accounts);

		sigUsage = mock(SigValueObj.class);
		usageEstimator = mock(CryptoFeeBuilder.class);

		subject = new CryptoUpdateResourceUsage(usageEstimator);
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(cryptoUpdateTxn));
		assertFalse(subject.applicableTo(nonCryptoUpdateTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// setup:
		FeeData exp = mock(FeeData.class);

		given(usageEstimator.getCryptoUpdateTxFeeMatrices(cryptoUpdateTxn, sigUsage, expiry, currKey)).willReturn(exp);

		// when:
		FeeData actual = subject.usageGiven(cryptoUpdateTxn, sigUsage, view);

		// then:
		verify(usageEstimator).getCryptoUpdateTxFeeMatrices(cryptoUpdateTxn, sigUsage, expiry, currKey);
		assertEquals(exp, actual);
	}

	@Test
	public void returnsDefaultUsageOnException() throws Exception {
		// when:
		FeeData actual = subject.usageGiven(cryptoUpdateTxn, sigUsage, null);

		// then:
		assertEquals(FeeData.getDefaultInstance(), actual);
	}
}
