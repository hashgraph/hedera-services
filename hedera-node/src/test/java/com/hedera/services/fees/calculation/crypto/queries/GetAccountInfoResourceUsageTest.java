package com.hedera.services.fees.calculation.crypto.queries;

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

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoQuery;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import static com.hedera.test.utils.IdUtils.*;
import static com.hederahashgraph.api.proto.java.ResponseType.*;

@RunWith(JUnitPlatform.class)
class GetAccountInfoResourceUsageTest {
	StateView view;
	CryptoFeeBuilder usageEstimator;
	FCMap<MerkleEntityId, MerkleAccount> accounts;
	GetAccountInfoResourceUsage subject;
	Key aKey = Key.newBuilder().setEd25519(ByteString.copyFrom("NONSENSE".getBytes())).build();
	String a = "0.0.1234";
	MerkleAccount aValue;

	PropertySource propertySource;

	@BeforeEach
	private void setup() throws Throwable {
		aValue = MerkleAccountFactory.newAccount().accountKeys(aKey).get();
		usageEstimator = mock(CryptoFeeBuilder.class);
		accounts = mock(FCMap.class);
		propertySource = mock(PropertySource.class);
		view = new StateView(StateView.EMPTY_TOPICS_SUPPLIER, () -> accounts, propertySource, null);

		subject = new GetAccountInfoResourceUsage(usageEstimator);
	}

	@Test
	public void throwsIaeWhenAccountIsntKosher() {
		// given:
		Query query = accountInfoQuery(a, ANSWER_ONLY);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.usageGiven(query, view));
	}

	@Test
	public void invokesEstimatorAsExpectedForType() {
		// setup:
		FeeData costAnswerUsage = mock(FeeData.class);
		FeeData answerOnlyUsage = mock(FeeData.class);
		MerkleEntityId key = MerkleEntityId.fromAccountId(asAccount(a));

		// given:
		Query answerOnlyQuery = accountInfoQuery(a, ANSWER_ONLY);
		Query costAnswerQuery = accountInfoQuery(a, COST_ANSWER);
		given(accounts.get(key)).willReturn(aValue);
		given(usageEstimator.getAccountInfoQueryFeeMatrices(aKey, Collections.EMPTY_LIST, COST_ANSWER))
			.willReturn(costAnswerUsage);
		given(usageEstimator.getAccountInfoQueryFeeMatrices(aKey, Collections.EMPTY_LIST, ANSWER_ONLY))
				.willReturn(answerOnlyUsage);

		// when:
		FeeData costAnswerEstimate = subject.usageGiven(costAnswerQuery, view);
		FeeData answerOnlyEstimate = subject.usageGiven(answerOnlyQuery, view);

		// then:
		assertTrue(costAnswerEstimate == costAnswerUsage);
		assertTrue(answerOnlyEstimate == answerOnlyUsage);
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
