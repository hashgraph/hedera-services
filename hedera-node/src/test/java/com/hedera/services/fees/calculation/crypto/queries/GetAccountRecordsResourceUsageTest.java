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
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsQuery;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import static com.hedera.test.utils.IdUtils.*;
import static com.hederahashgraph.api.proto.java.ResponseType.*;
import static com.hedera.services.state.serdes.DomainSerdesTest.recordOne;
import static com.hedera.services.state.serdes.DomainSerdesTest.recordTwo;

class GetAccountRecordsResourceUsageTest {
	StateView view;
	CryptoFeeBuilder usageEstimator;
	FCMap<MerkleEntityId, MerkleAccount> accounts;
	GetAccountRecordsResourceUsage subject;
	String a = "0.0.1234";
	MerkleAccount aValue;
	List<TransactionRecord> someRecords = ExpirableTxnRecord.allToGrpc(List.of(recordOne(), recordTwo()));
	PropertySource propertySource;

	@BeforeEach
	private void setup() throws Throwable {
		aValue = MerkleAccountFactory.newAccount().get();
		aValue.records().offer(recordOne());
		aValue.records().offer(recordTwo());
		usageEstimator = mock(CryptoFeeBuilder.class);
		accounts = mock(FCMap.class);
		propertySource = mock(PropertySource.class);
		view = new StateView(StateView.EMPTY_TOPICS_SUPPLIER, () -> accounts, propertySource, null);

		subject = new GetAccountRecordsResourceUsage(new AnswerFunctions(), usageEstimator);
	}

	@Test
	public void returnsEmptyFeeDataWhenAccountMissing() {
		// given:
		Query query = accountRecordsQuery(a, ANSWER_ONLY);

		// expect:
		Assertions.assertSame(FeeData.getDefaultInstance(), subject.usageGiven(query, view));
	}

	@Test
	public void invokesEstimatorAsExpectedForType() {
		// setup:
		FeeData costAnswerUsage = mock(FeeData.class);
		FeeData answerOnlyUsage = mock(FeeData.class);
		MerkleEntityId key = MerkleEntityId.fromAccountId(asAccount(a));

		// given:
		Query answerOnlyQuery = accountRecordsQuery(a, ANSWER_ONLY);
		Query costAnswerQuery = accountRecordsQuery(a, COST_ANSWER);
		given(accounts.get(key)).willReturn(aValue);
		given(accounts.containsKey(key)).willReturn(true);
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
