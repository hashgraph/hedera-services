package com.hedera.services.fees.calculation.crypto.queries;

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

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.fees.calculation.file.queries.GetFileInfoResourceUsage;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.usage.crypto.CryptoGetInfoUsage;
import com.hedera.services.usage.crypto.CryptoOpsUsage;
import com.hedera.services.usage.crypto.ExtantCryptoContext;
import com.hedera.services.usage.file.FileOpsUsage;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoQuery;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenRelationship;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.function.Function;

import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asFile;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class GetAccountInfoResourceUsageTest {
	StateView view;
	FCMap<MerkleEntityId, MerkleAccount> accounts;
	GetAccountInfoResourceUsage subject;
	Key aKey = Key.newBuilder().setEd25519(ByteString.copyFrom("NONSENSE".getBytes())).build();
	String a = "0.0.1234";
	MerkleAccount aValue;
	long expiry = 1_234_567L;
	AccountID proxy = IdUtils.asAccount("0.0.75231");
	TokenID aToken = asToken("0.0.1001");
	TokenID bToken = asToken("0.0.1002");
	TokenID cToken = asToken("0.0.1003");
	String memo = "Hi there!";
	FeeData expected;
	AccountID queryTarget = IdUtils.asAccount(a);

	CryptoOpsUsage cryptoOpsUsage;

	@BeforeEach
	private void setup() throws Throwable {
		cryptoOpsUsage = mock(CryptoOpsUsage.class);
		expected = mock(FeeData.class);
		view = mock(StateView.class);

		subject = new GetAccountInfoResourceUsage(cryptoOpsUsage);
	}

	@Test
	public void usesEstimator() {
		// setup:
		ArgumentCaptor<ExtantCryptoContext> captor = ArgumentCaptor.forClass(ExtantCryptoContext.class);

		var info = CryptoGetInfoResponse.AccountInfo.newBuilder()
				.setExpirationTime(Timestamp.newBuilder().setSeconds(expiry))
				.setMemo(memo)
				.setProxyAccountID(proxy)
				.setKey(aKey)
				.addTokenRelationships(0, TokenRelationship.newBuilder().setTokenId(aToken))
				.addTokenRelationships(1, TokenRelationship.newBuilder().setTokenId(bToken))
				.addTokenRelationships(2, TokenRelationship.newBuilder().setTokenId(cToken))
				.build();
		// and:
		var query = accountInfoQuery(a, ANSWER_ONLY);

		given(view.infoForAccount(queryTarget)).willReturn(Optional.of(info));
		given(cryptoOpsUsage.cryptoInfoUsage(any(), any())).willReturn(expected);

		// when:
		var usage = subject.usageGiven(query, view);

		// then:
		assertEquals(expected, usage);
		// and:
		verify(cryptoOpsUsage).cryptoInfoUsage(argThat(query::equals), captor.capture());
		// and:
		var ctx = captor.getValue();
		assertEquals(aKey, ctx.currentKey());
		assertEquals(expiry, ctx.currentExpiry());
		assertEquals(memo, ctx.currentMemo());
		assertEquals(3, ctx.currentNumTokenRels());
		assertTrue(ctx.currentlyHasProxy());
	}

	@Test
	public void returnsDefaultIfNoSuchAccount() {
		given(view.infoForAccount(queryTarget)).willReturn(Optional.empty());

		// when:
		var usage = subject.usageGiven(accountInfoQuery(a, ANSWER_ONLY), view);

		// then:
		assertSame(FeeData.getDefaultInstance(), usage);
	}

	@Test
	public void recognizesApplicableQuery() {
		// given:
		Query accountInfoQuery = accountInfoQuery(a, COST_ANSWER);
		Query nonAccountInfoQuery = nonAccountInfoQuery();

		// expect:
		assertTrue(subject.applicableTo(accountInfoQuery));
		assertFalse(subject.applicableTo(nonAccountInfoQuery));
	}

	private Query accountInfoQuery(String target, ResponseType type) {
		AccountID id = asAccount(target);
		CryptoGetInfoQuery.Builder op = CryptoGetInfoQuery.newBuilder()
				.setAccountID(id)
				.setHeader(QueryHeader.newBuilder().setResponseType(type));
		return Query.newBuilder()
				.setCryptoGetInfo(op)
				.build();
	}

	private Query nonAccountInfoQuery() {
		return Query.newBuilder().build();
	}
}
