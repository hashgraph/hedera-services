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

import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsQuery;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.services.state.serdes.DomainSerdesTest.recordOne;
import static com.hedera.services.state.serdes.DomainSerdesTest.recordTwo;
import static com.hedera.services.store.tokens.views.EmptyUniqTokenViewFactory.EMPTY_UNIQ_TOKEN_VIEW_FACTORY;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

class GetAccountRecordsResourceUsageTest {
	private StateView view;
	private CryptoFeeBuilder usageEstimator;
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	private GetAccountRecordsResourceUsage subject;
	private String a = "0.0.1234";
	private MerkleAccount aValue;
	private List<TransactionRecord> someRecords = ExpirableTxnRecord.allToGrpc(List.of(recordOne(), recordTwo()));
	private NodeLocalProperties nodeProps;

	@BeforeEach
	private void setup() {
		aValue = MerkleAccountFactory.newAccount().get();
		aValue.records().offer(recordOne());
		aValue.records().offer(recordTwo());
		usageEstimator = mock(CryptoFeeBuilder.class);
		accounts = mock(MerkleMap.class);
		nodeProps = mock(NodeLocalProperties.class);
		final MutableStateChildren children = new MutableStateChildren();
		children.setAccounts(accounts);
		view = new StateView(
				null,
				null,
				children,
				EMPTY_UNIQ_TOKEN_VIEW_FACTORY,
				null);

		subject = new GetAccountRecordsResourceUsage(new AnswerFunctions(), usageEstimator);
	}

	@Test
	void returnsEmptyFeeDataWhenAccountMissing() {
		final var query = accountRecordsQuery(a, ANSWER_ONLY);

		assertSame(FeeData.getDefaultInstance(), subject.usageGiven(query, view));
	}

	@Test
	void invokesEstimatorAsExpectedForType() {
		// setup:
		final var costAnswerUsage = mock(FeeData.class);
		final var answerOnlyUsage = mock(FeeData.class);
		final var key = EntityNum.fromAccountId(asAccount(a));

		// given:
		final var answerOnlyQuery = accountRecordsQuery(a, ANSWER_ONLY);
		final var costAnswerQuery = accountRecordsQuery(a, COST_ANSWER);
		given(accounts.get(key)).willReturn(aValue);
		given(accounts.containsKey(key)).willReturn(true);
		given(usageEstimator.getCryptoAccountRecordsQueryFeeMatrices(someRecords, COST_ANSWER))
				.willReturn(costAnswerUsage);
		given(usageEstimator.getCryptoAccountRecordsQueryFeeMatrices(someRecords, ANSWER_ONLY))
				.willReturn(answerOnlyUsage);

		final var costAnswerEstimate = subject.usageGiven(costAnswerQuery, view);
		final var answerOnlyEstimate = subject.usageGiven(answerOnlyQuery, view);

		assertSame(costAnswerUsage, costAnswerEstimate);
		assertSame(answerOnlyUsage, answerOnlyEstimate);
	}


	@Test
	void recognizesApplicableQuery() {
		final var accountRecordsQuery = accountRecordsQuery(a, COST_ANSWER);
		final var nonAccountRecordsQuery = nonAccountRecordsQuery();

		assertTrue(subject.applicableTo(accountRecordsQuery));
		assertFalse(subject.applicableTo(nonAccountRecordsQuery));
	}

	private Query accountRecordsQuery(final String target, final ResponseType type) {
		final var id = asAccount(target);
		final var op = CryptoGetAccountRecordsQuery.newBuilder()
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
