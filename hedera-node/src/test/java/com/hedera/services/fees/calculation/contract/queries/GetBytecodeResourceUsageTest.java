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
import com.hederahashgraph.api.proto.java.ContractGetBytecodeQuery;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.hedera.test.utils.IdUtils.asContract;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

class GetBytecodeResourceUsageTest {
	byte[] bytecode = "A Supermarket in California".getBytes();
	ContractID target = asContract("0.0.123");
	StateView view;
	SmartContractFeeBuilder usageEstimator;

	GetBytecodeResourceUsage subject;

	@BeforeEach
	private void setup() throws Throwable {
		usageEstimator = mock(SmartContractFeeBuilder.class);
		view = mock(StateView.class);

		subject = new GetBytecodeResourceUsage(usageEstimator);
	}

	@Test
	public void recognizesApplicableQuery() {
		// given:
		var applicable = bytecodeQuery(target, COST_ANSWER);
		var inapplicable = Query.getDefaultInstance();

		// expect:
		assertTrue(subject.applicableTo(applicable));
		assertFalse(subject.applicableTo(inapplicable));
	}

	@Test
	public void invokesEstimatorAsExpectedForType() {
		// setup:
		FeeData costAnswerUsage = mock(FeeData.class);
		FeeData answerOnlyUsage = mock(FeeData.class);
		int size = bytecode.length;

		// given:
		Query answerOnlyQuery = bytecodeQuery(target, ANSWER_ONLY);
		Query costAnswerQuery = bytecodeQuery(target, COST_ANSWER);
		// and:
		given(view.bytecodeOf(target)).willReturn(Optional.of(bytecode));
		// and:
		given(usageEstimator.getContractByteCodeQueryFeeMatrices(size, COST_ANSWER))
				.willReturn(costAnswerUsage);
		given(usageEstimator.getContractByteCodeQueryFeeMatrices(size, ANSWER_ONLY))
				.willReturn(answerOnlyUsage);

		// when:
		FeeData costAnswerEstimate = subject.usageGiven(costAnswerQuery, view);
		FeeData answerOnlyEstimate = subject.usageGiven(answerOnlyQuery, view);

		// then:
		assertSame(costAnswerEstimate, costAnswerUsage);
		assertSame(answerOnlyEstimate, answerOnlyUsage);
	}

	private Query bytecodeQuery(ContractID id, ResponseType type) {
		ContractGetBytecodeQuery.Builder op = ContractGetBytecodeQuery.newBuilder()
				.setContractID(id)
				.setHeader(QueryHeader.newBuilder().setResponseType(type));
		return Query.newBuilder()
				.setContractGetBytecode(op)
				.build();
	}
}
