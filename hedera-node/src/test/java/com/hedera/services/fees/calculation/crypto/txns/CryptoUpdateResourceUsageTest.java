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

import static com.hedera.services.state.merkle.MerkleAccountState.DEFAULT_MEMO;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.hedera.services.fees.calculation.file.txns.FileUpdateResourceUsage;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.usage.crypto.CryptoOpsUsage;
import com.hedera.services.usage.crypto.ExtantCryptoContext;
import com.hedera.services.usage.file.ExtantFileContext;
import com.hedera.services.usage.file.FileOpsUsage;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenRelationship;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import com.hederahashgraph.fee.SigValueObj;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.codec.digest.Crypt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;


import java.util.Optional;

import static org.mockito.BDDMockito.*;
import static com.hedera.test.utils.IdUtils.*;

class CryptoUpdateResourceUsageTest {
	long expiry = 1_234_567L;
	Key currKey = Key.newBuilder().setEd25519(ByteString.copyFrom("NONSENSE".getBytes())).build();
	AccountID proxy = IdUtils.asAccount("0.0.4321");
	AccountID target = asAccount("0.0.1234");
	String memo = "Though like waves breaking it may be";
	TokenID aToken = asToken("0.0.1001");
	TokenID bToken = asToken("0.0.1002");
	TokenID cToken = asToken("0.0.1003");
	StateView view;

	int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
	SigValueObj svo = new SigValueObj(numSigs, numPayerKeys, sigsSize);
	private CryptoOpsUsage cryptoOpsUsage;

	FeeData expected;

	private CryptoUpdateResourceUsage subject;

	private TransactionBody nonCryptoUpdateTxn;
	private TransactionBody cryptoUpdateTxn;

	@BeforeEach
	private void setup() {
		cryptoUpdateTxn = mock(TransactionBody.class);
		CryptoUpdateTransactionBody update = mock(CryptoUpdateTransactionBody.class);
		given(update.getAccountIDToUpdate()).willReturn(target);
		given(cryptoUpdateTxn.hasCryptoUpdateAccount()).willReturn(true);
		given(cryptoUpdateTxn.getCryptoUpdateAccount()).willReturn(update);

		cryptoOpsUsage = mock(CryptoOpsUsage.class);

		nonCryptoUpdateTxn = mock(TransactionBody.class);
		given(nonCryptoUpdateTxn.hasCryptoUpdateAccount()).willReturn(false);

		view = mock(StateView.class);

		subject = new CryptoUpdateResourceUsage(cryptoOpsUsage);
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(cryptoUpdateTxn));
		assertFalse(subject.applicableTo(nonCryptoUpdateTxn));
	}

	@Test
	public void returnsAsExpectedWhenAvail() throws Exception {
		// setup:
		expected = mock(FeeData.class);
		// and:
		var info = CryptoGetInfoResponse.AccountInfo.newBuilder()
				.setExpirationTime(Timestamp.newBuilder().setSeconds(expiry))
				.setMemo(memo)
				.setKey(currKey)
				.setProxyAccountID(proxy)
				.addTokenRelationships(0, TokenRelationship.newBuilder().setTokenId(aToken))
				.addTokenRelationships(1, TokenRelationship.newBuilder().setTokenId(bToken))
				.addTokenRelationships(2, TokenRelationship.newBuilder().setTokenId(cToken))
				.build();
		// and:
		ArgumentCaptor<ExtantCryptoContext> captor = ArgumentCaptor.forClass(ExtantCryptoContext.class);

		given(cryptoOpsUsage.cryptoUpdateUsage(any(), any(), captor.capture())).willReturn(expected);
		given(view.infoForAccount(target)).willReturn(Optional.of(info));

		// when:
		var actual = subject.usageGiven(cryptoUpdateTxn, svo, view);

		// then:
		assertSame(expected, actual);
		// and:
		var ctxUsed = captor.getValue();
		assertEquals(expiry, ctxUsed.currentExpiry());
		assertEquals(memo, ctxUsed.currentMemo());
		assertEquals(currKey, ctxUsed.currentKey());
		assertEquals(3, ctxUsed.currentNumTokenRels());
		assertTrue(ctxUsed.currentlyHasProxy());
	}

	@Test
	public void delegatesToCorrectEstimateWhenUnknown() throws Exception {
		// setup:
		long now = 1_234_567L;
		expected = mock(FeeData.class);
		// and:
		ArgumentCaptor<ExtantCryptoContext> captor = ArgumentCaptor.forClass(ExtantCryptoContext.class);
		// and:
		TransactionID txnId = TransactionID.newBuilder()
				.setTransactionValidStart(Timestamp.newBuilder().setSeconds(now).build())
				.build();

		given(cryptoUpdateTxn.getTransactionID()).willReturn(txnId);
		given(cryptoOpsUsage.cryptoUpdateUsage(any(), any(), captor.capture())).willReturn(expected);
		given(view.infoForAccount(target)).willReturn(Optional.empty());

		// when:
		var actual = subject.usageGiven(cryptoUpdateTxn, svo, view);

		// then:
		assertSame(expected, actual);
		// and:
		var ctxUsed = captor.getValue();
		assertEquals(now, ctxUsed.currentExpiry());
	}

	@Test
	public void missingCtxScans() {
		// setup:
		long now = 1_234_567L;

		// given:
		var ctx = CryptoUpdateResourceUsage.missingCtx(now);

		// expect:
		assertEquals(0, ctx.currentNumTokenRels());
		assertEquals(now, ctx.currentExpiry());
		Assertions.assertSame(Key.getDefaultInstance(), ctx.currentKey());
		Assertions.assertSame(DEFAULT_MEMO, ctx.currentMemo());
		Assertions.assertFalse(ctx.currentlyHasProxy());
	}
}
