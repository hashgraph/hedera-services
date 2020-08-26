package com.hedera.services.fees.calculation.contract.queries;

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

import com.hedera.services.context.AwareTransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.queries.contract.GetContractInfoAnswer;
import com.hedera.services.queries.meta.GetTxnRecordAnswer;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.test.factories.accounts.MapValueFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ContractGetBytecodeQuery;
import com.hederahashgraph.api.proto.java.ContractGetInfoQuery;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Optional;

import static com.hedera.services.fees.calculation.contract.queries.GetContractInfoResourceUsage.MISSING_KEY_STANDIN;
import static com.hedera.services.fees.calculation.crypto.queries.GetTxnRecordResourceUsage.MISSING_RECORD_STANDIN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class GetContractInfoResourceUsageTest {
	byte[] storage = "The Ecstasy".getBytes();
	byte[] bytecode = "A Supermarket in California".getBytes();
	ContractID target = asContract("0.0.123");

	StateView view;
	SmartContractFeeBuilder usageEstimator;
	ContractGetInfoResponse.ContractInfo info;

	Query satisfiableAnswerOnly = contractInfoQuery(target, ANSWER_ONLY);
	Query satisfiableCostAnswer = contractInfoQuery(target, COST_ANSWER);

	GetContractInfoResourceUsage subject;

	@BeforeEach
	private void setup() throws Throwable {
		info = ContractGetInfoResponse.ContractInfo.newBuilder()
				.setAdminKey(COMPLEX_KEY_ACCOUNT_KT.asKey())
				.build();

		usageEstimator = mock(SmartContractFeeBuilder.class);
		view = mock(StateView.class);

		given(view.infoForContract(target)).willReturn(Optional.of(info));

		subject = new GetContractInfoResourceUsage(usageEstimator);
	}

	@Test
	public void recognizesApplicableQuery() {
		// given:
		var applicable = contractInfoQuery(target, COST_ANSWER);
		var inapplicable = Query.getDefaultInstance();

		// expect:
		assertTrue(subject.applicableTo(applicable));
		assertFalse(subject.applicableTo(inapplicable));
	}

	@Test
	public void invokesEstimatorAsExpectedForType() throws Exception {
		// setup:
		FeeData costAnswerUsage = mock(FeeData.class);
		FeeData answerOnlyUsage = mock(FeeData.class);

		// given:
		given(usageEstimator.getContractInfoQueryFeeMatrices(info.getAdminKey(), COST_ANSWER))
				.willReturn(costAnswerUsage);
		given(usageEstimator.getContractInfoQueryFeeMatrices(info.getAdminKey(), ANSWER_ONLY))
				.willReturn(answerOnlyUsage);

		// when:
		FeeData costAnswerEstimate = subject.usageGiven(satisfiableCostAnswer, view);
		FeeData answerOnlyEstimate = subject.usageGiven(satisfiableAnswerOnly, view);

		// then:
		assertSame(costAnswerEstimate, costAnswerUsage);
		assertSame(answerOnlyEstimate, answerOnlyUsage);

		// and when:
		costAnswerEstimate = subject.usageGivenType(satisfiableCostAnswer, view, COST_ANSWER);
		answerOnlyEstimate = subject.usageGivenType(satisfiableAnswerOnly, view, ANSWER_ONLY);

		// then:
		assertSame(costAnswerEstimate, costAnswerUsage);
		assertSame(answerOnlyEstimate, answerOnlyUsage);
	}

	@Test
	public void setsInfoInQueryCxtIfPresent() {
		// setup:
		FeeData answerOnlyUsage = mock(FeeData.class);
		var queryCtx = new HashMap<String, Object>();

		// given:
		given(usageEstimator.getContractInfoQueryFeeMatrices(info.getAdminKey(), ANSWER_ONLY))
				.willReturn(answerOnlyUsage);

		// when:
		subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

		// then:
		assertSame(info, queryCtx.get(GetContractInfoAnswer.CONTRACT_INFO_CTX_KEY));
	}

	@Test
	public void onlySetsContractInfoInQueryCxtIfFound() {
		// setup:
		FeeData answerOnlyUsage = mock(FeeData.class);
		var queryCtx = new HashMap<String, Object>();

		given(usageEstimator.getContractInfoQueryFeeMatrices(MISSING_KEY_STANDIN, ANSWER_ONLY))
				.willReturn(answerOnlyUsage);
		given(view.infoForContract(target)).willReturn(Optional.empty());

		// when:
		var actual = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

		// then:
		assertFalse(queryCtx.containsKey(GetContractInfoAnswer.CONTRACT_INFO_CTX_KEY));
		assertSame(answerOnlyUsage, actual);
	}

	@Test
	public void rethrowsIae() {
		// given:
		Query query = contractInfoQuery(target, ANSWER_ONLY);
		given(usageEstimator.getContractInfoQueryFeeMatrices(any(), any()))
				.willThrow(IllegalStateException.class);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.usageGiven(query, view));
	}

	private Query contractInfoQuery(ContractID id, ResponseType type) {
		ContractGetInfoQuery.Builder op = ContractGetInfoQuery.newBuilder()
				.setContractID(id)
				.setHeader(QueryHeader.newBuilder().setResponseType(type));
		return Query.newBuilder()
				.setContractGetInfo(op)
				.build();
	}
}