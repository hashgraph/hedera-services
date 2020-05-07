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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hedera.test.factories.accounts.MapValueFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsQuery;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import com.hedera.services.legacy.core.MapKey;
import com.hedera.services.context.domain.haccount.HederaAccount;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.List;

import static com.hedera.services.legacy.core.MapKey.getMapKey;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import static com.hedera.test.utils.IdUtils.*;
import static com.hederahashgraph.api.proto.java.ResponseType.*;
import static com.hedera.services.legacy.core.jproto.JTransactionRecord.convert;
import static com.hedera.services.context.domain.serdes.DomainSerdesTest.recordOne;
import static com.hedera.services.context.domain.serdes.DomainSerdesTest.recordTwo;

@RunWith(JUnitPlatform.class)
class GetAccountRecordsResourceUsageTest {
	StateView view;
	CryptoFeeBuilder usageEstimator;
	FCMap<MapKey, HederaAccount> accounts;
	GetAccountRecordsResourceUsage subject;
	String a = "0.0.1234";
	HederaAccount aValue;
	List<TransactionRecord> someRecords = convert(List.of(recordOne(), recordTwo()));

	@BeforeEach
	private void setup() throws Throwable {
		aValue = MapValueFactory.newAccount().get();
		aValue.getRecords().offer(recordOne());
		aValue.getRecords().offer(recordTwo());
		usageEstimator = mock(CryptoFeeBuilder.class);
		accounts = mock(FCMap.class);
		view = new StateView(StateView.EMPTY_TOPICS, accounts);

		subject = new GetAccountRecordsResourceUsage(new AnswerFunctions(), usageEstimator);
	}

	@Test
	public void throwsIaeWhenAccountIsntKosher() {
		// given:
		Query query = accountRecordsQuery(a, ANSWER_ONLY);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.usageGiven(query, view));
	}

	@Test
	public void invokesEstimatorAsExpectedForType() {
		// setup:
		FeeData costAnswerUsage = mock(FeeData.class);
		FeeData answerOnlyUsage = mock(FeeData.class);
		MapKey key = getMapKey(asAccount(a));

		// given:
		Query answerOnlyQuery = accountRecordsQuery(a, ANSWER_ONLY);
		Query costAnswerQuery = accountRecordsQuery(a, COST_ANSWER);
		given(accounts.get(key)).willReturn(aValue);
		given(usageEstimator.getCryptoAccountRecordsQueryFeeMatrices(someRecords, COST_ANSWER))
				.willReturn(costAnswerUsage);
		given(usageEstimator.getCryptoAccountRecordsQueryFeeMatrices(someRecords, ANSWER_ONLY))
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
		Query accountRecordsQuery = accountRecordsQuery(a, COST_ANSWER);
		Query nonAccountRecordsQuery = nonAccountRecordsQuery();

		// expect:
		assertTrue(subject.applicableTo(accountRecordsQuery));
		assertFalse(subject.applicableTo(nonAccountRecordsQuery));
	}

	private Query accountRecordsQuery(String target, ResponseType type) {
		AccountID id = asAccount(target);
		CryptoGetAccountRecordsQuery.Builder op = CryptoGetAccountRecordsQuery.newBuilder()
				.setAccountID(id)
				.setHeader(QueryHeader.newBuilder().setResponseType(type));
		return Query.newBuilder()
				.setCryptoGetAccountRecords(op)
				.build();
	}

	private Query nonAccountRecordsQuery() {
		return Query.newBuilder().build();
	}
}
