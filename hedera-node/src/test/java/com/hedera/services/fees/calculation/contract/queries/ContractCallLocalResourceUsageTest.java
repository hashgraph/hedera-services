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

import com.google.protobuf.ByteString;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.calculation.UsageEstimatorUtils;
import com.hedera.services.queries.contract.ContractCallLocalAnswer;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.HashMap;

import static com.hedera.test.utils.IdUtils.asContract;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@RunWith(JUnitPlatform.class)
class ContractCallLocalResourceUsageTest {
	int gas = 1_234;
	ByteString params = ByteString.copyFrom("Hungry, and...".getBytes());
	ContractID target = asContract("0.0.123");

	ByteString result = ByteString.copyFrom("Searching for images".getBytes());

	StateView view;
	SmartContractFeeBuilder usageEstimator;
	GlobalDynamicProperties properties = new MockGlobalDynamicProps();
	ContractCallLocalAnswer.LegacyLocalCaller delegate;

	Query satisfiableCostAnswer = localCallQuery(target, COST_ANSWER);
	Query satisfiableAnswerOnly = localCallQuery(target, ANSWER_ONLY);

	ContractCallLocalResourceUsage subject;

	@BeforeEach
	private void setup() throws Throwable {
		view = mock(StateView.class);
		delegate = mock(ContractCallLocalAnswer.LegacyLocalCaller.class);
		usageEstimator = mock(SmartContractFeeBuilder.class);

		subject = new ContractCallLocalResourceUsage(delegate, usageEstimator, properties);
	}

	@Test
	public void recognizesApplicableQuery() {
		// given:
		var applicable = localCallQuery(target, COST_ANSWER);
		var inapplicable = Query.getDefaultInstance();

		// expect:
		assertTrue(subject.applicableTo(applicable));
		assertFalse(subject.applicableTo(inapplicable));
	}

	@Test
	public void setsResultInQueryCxtIfPresent() throws Exception {
		// setup:
		var queryCtx = new HashMap<String, Object>();
		var response = okResponse();
		var estimateResponse = subject.dummyResponse(target);
		var expected = expectedUsage();

		given(delegate.perform(argThat(satisfiableAnswerOnly.getContractCallLocal()::equals), anyLong()))
				.willReturn(response);
		given(usageEstimator.getContractCallLocalFeeMatrices(
				params.size(),
				response.getFunctionResult(),
				ANSWER_ONLY)).willReturn(nonGasUsage);
		given(usageEstimator.getContractCallLocalFeeMatrices(
				params.size(),
				estimateResponse.getFunctionResult(),
				ANSWER_ONLY)).willReturn(nonGasUsage);

		// when:
		var actualUsage1 = subject.usageGiven(satisfiableAnswerOnly, view);
		var actualUsage2 = subject.usageGivenType(satisfiableAnswerOnly, view, ANSWER_ONLY);
		var actualUsage3 = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

		// then:
		assertSame(response, queryCtx.get(ContractCallLocalAnswer.CONTRACT_CALL_LOCAL_CTX_KEY));
		assertEquals(expected, actualUsage1);
		assertEquals(expected, actualUsage2);
		assertEquals(expected, actualUsage3);
	}

	@Test
	public void treatsAnswerOnlyEstimateAsExpected() {
		// setup:
		var response = subject.dummyResponse(target);
		var expected = expectedUsage();

		given(usageEstimator.getContractCallLocalFeeMatrices(
				params.size(),
				response.getFunctionResult(),
				ANSWER_ONLY)).willReturn(nonGasUsage);

		// when:
		var actualUsage = subject.usageGivenType(satisfiableCostAnswer, view, ANSWER_ONLY);

		// then:
		assertEquals(expected, actualUsage);
		verifyNoInteractions(delegate);
	}

	@Test
	public void translatesDelegateException() throws Exception {
		// setup:
		var queryCtx = new HashMap<String, Object>();

		given(delegate.perform(any(), anyLong())).willThrow(Exception.class);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.usageGiven(satisfiableAnswerOnly, view, queryCtx));
		// and:
		assertFalse(queryCtx.containsKey(ContractCallLocalAnswer.CONTRACT_CALL_LOCAL_CTX_KEY));
	}

	@Test
	public void dummyResponseAsExpected() {
		// given:
		var dummy = subject.dummyResponse(target);

		// then:
		assertEquals(OK, dummy.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(target, dummy.getFunctionResult().getContractID());
		assertEquals(properties.localCallEstRetBytes(), dummy.getFunctionResult().getContractCallResult().size());
	}


	private Query localCallQuery(ContractID id, ResponseType type) {
		ContractCallLocalQuery.Builder op = ContractCallLocalQuery.newBuilder()
				.setContractID(id)
				.setGas(gas)
				.setFunctionParameters(params)
				.setHeader(QueryHeader.newBuilder().setResponseType(type));
		return Query.newBuilder()
				.setContractCallLocal(op)
				.build();
	}

	private ContractCallLocalResponse okResponse() {
		return response(OK);
	}

	private ContractCallLocalResponse response(ResponseCodeEnum status) {
		return ContractCallLocalResponse.newBuilder()
				.setHeader(ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(status))
				.setFunctionResult(ContractFunctionResult.newBuilder()
						.setContractCallResult(result))
				.build();
	}

	static final FeeData nonGasUsage = UsageEstimatorUtils.defaultPartitioning(
			FeeComponents.newBuilder()
					.setMin(1)
					.setMax(1_000_000)
					.setConstant(1)
					.setBpt(1)
					.setVpt(1)
					.setRbh(1)
					.setSbh(1)
					.setGas(0)
					.setTv(1)
					.setBpr(1)
					.setSbpr(1)
					.build(), 1);

	private FeeData expectedUsage() {
		return nonGasUsage.toBuilder()
				.setNodedata(nonGasUsage.toBuilder().getNodedataBuilder().setGas(gas).build())
				.build();
	}
}
