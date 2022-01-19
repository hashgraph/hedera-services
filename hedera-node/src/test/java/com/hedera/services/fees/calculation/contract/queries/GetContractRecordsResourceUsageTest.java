package com.hedera.services.fees.calculation.contract.queries;

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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.contract.GetContractRecordsAnswer;
import com.hedera.services.store.tokens.views.EmptyUniqTokenViewFactory;
import com.hederahashgraph.api.proto.java.ContractGetRecordsQuery;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.test.utils.QueryUtils.queryHeaderOf;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

class GetContractRecordsResourceUsageTest {
	private StateView view;
	private SmartContractFeeBuilder usageEstimator;
	private GetContractRecordsResourceUsage subject;
	private static final String a = "0.0.1234";

	@BeforeEach
	private void setup() {
		usageEstimator = mock(SmartContractFeeBuilder.class);
		view = new StateView(
				null,
				null,
				null,
				EmptyUniqTokenViewFactory.EMPTY_UNIQ_TOKEN_VIEW_FACTORY,
				null);

		subject = new GetContractRecordsResourceUsage(usageEstimator);
	}

	@Test
	void invokesEstimatorAsExpectedForType() {
		final var costAnswerUsage = mock(FeeData.class);
		final var answerOnlyUsage = mock(FeeData.class);
		final var answerOnlyQuery = accountRecordsQuery(a, ANSWER_ONLY);
		final var costAnswerQuery = accountRecordsQuery(a, COST_ANSWER);
		given(usageEstimator.getContractRecordsQueryFeeMatrices(
				GetContractRecordsAnswer.GUARANTEED_EMPTY_PAYER_RECORDS,
				COST_ANSWER)).willReturn(costAnswerUsage);
		given(usageEstimator.getContractRecordsQueryFeeMatrices(
				GetContractRecordsAnswer.GUARANTEED_EMPTY_PAYER_RECORDS,
				ANSWER_ONLY)).willReturn(answerOnlyUsage);

		final var costAnswerEstimate = subject.usageGiven(costAnswerQuery, view);
		final var answerOnlyEstimate = subject.usageGiven(answerOnlyQuery, view);

		assertSame(costAnswerUsage, costAnswerEstimate);
		assertSame(answerOnlyUsage, answerOnlyEstimate);
	}

	@Test
	void recognizesApplicableQuery() {
		final var accountRecordsQuery = accountRecordsQuery(a, COST_ANSWER);
		final var nonContractRecordsQuery = Query.getDefaultInstance();

		assertTrue(subject.applicableTo(accountRecordsQuery));
		assertFalse(subject.applicableTo(nonContractRecordsQuery));
	}

	private Query accountRecordsQuery(final String target, final ResponseType type) {
		final var id = asContract(target);
		final var op = ContractGetRecordsQuery.newBuilder()
				.setContractID(id)
				.setHeader(queryHeaderOf(type));
		return Query.newBuilder()
				.setContractGetRecords(op)
				.build();
	}
}
