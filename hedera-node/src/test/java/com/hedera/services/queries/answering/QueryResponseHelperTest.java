package com.hedera.services.queries.answering;

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
import com.hedera.services.queries.AnswerFlow;
import com.hedera.services.queries.AnswerService;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hedera.services.legacy.services.stats.HederaNodeStats;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_START;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class QueryResponseHelperTest {
	Query query = Query.getDefaultInstance();
	String metric = "imaginary";
	Response okResponse;
	Response notOkResponse;

	AnswerFlow answerFlow;
	AnswerService answer;
	HederaNodeStats stats;
	StreamObserver<Response> observer;

	QueryResponseHelper subject;

	@BeforeEach
	private void setup() {
		answerFlow = mock(AnswerFlow.class);
		stats = mock(HederaNodeStats.class);
		answer = mock(AnswerService.class);
		observer = mock(StreamObserver.class);
		okResponse = mock(Response.class);
		notOkResponse = mock(Response.class);

		subject = new QueryResponseHelper(answerFlow, stats);
	}

	@Test
	public void helpsWithFileHappyPath() {
		// setup:
		InOrder inOrder = inOrder(answerFlow, stats, observer);

		given(answerFlow.satisfyUsing(answer, query)).willReturn(okResponse);
		given(answer.extractValidityFrom(okResponse)).willReturn(OK);

		// when:
		subject.respondToFile(query, observer, answer, metric);

		// then:
		inOrder.verify(stats).fileQueryReceived(metric);
		inOrder.verify(answerFlow).satisfyUsing(answer, query);
		inOrder.verify(observer).onNext(okResponse);
		inOrder.verify(observer).onCompleted();
		inOrder.verify(stats).fileQuerySubmitted(metric);
	}

	@Test
	public void helpsWithCryptoHappyPath() {
		// setup:
		InOrder inOrder = inOrder(answerFlow, stats, observer);

		given(answerFlow.satisfyUsing(answer, query)).willReturn(okResponse);
		given(answer.extractValidityFrom(okResponse)).willReturn(OK);

		// when:
		subject.respondToCrypto(query, observer, answer, metric);

		// then:
		inOrder.verify(stats).cryptoQueryReceived(metric);
		inOrder.verify(answerFlow).satisfyUsing(answer, query);
		inOrder.verify(observer).onNext(okResponse);
		inOrder.verify(observer).onCompleted();
		inOrder.verify(stats).cryptoQuerySubmitted(metric);
	}

	@Test
	public void helpsWithHcsHappyPath() {
		// setup:
		InOrder inOrder = inOrder(answerFlow, stats, observer);

		given(answerFlow.satisfyUsing(answer, query)).willReturn(okResponse);
		given(answer.extractValidityFrom(okResponse)).willReturn(OK);

		// when:
		subject.respondToHcs(query, observer, answer, metric);

		// then:
		inOrder.verify(stats).hcsQueryReceived(metric);
		inOrder.verify(answerFlow).satisfyUsing(answer, query);
		inOrder.verify(observer).onNext(okResponse);
		inOrder.verify(observer).onCompleted();
		inOrder.verify(stats).hcsQueryAnswered(metric);
	}

	@Test
	public void helpsWithUnhappyPath() {
		// setup:
		InOrder inOrder = inOrder(answerFlow, stats, observer);

		given(answerFlow.satisfyUsing(answer, query)).willReturn(notOkResponse);
		given(answer.extractValidityFrom(okResponse)).willReturn(INVALID_TRANSACTION_START);

		// when:
		subject.respondToCrypto(query, observer, answer, metric);

		// then:
		inOrder.verify(stats).cryptoQueryReceived(metric);
		inOrder.verify(answerFlow).satisfyUsing(answer, query);
		inOrder.verify(observer).onNext(notOkResponse);
		inOrder.verify(observer).onCompleted();
		inOrder.verify(stats, never()).cryptoQuerySubmitted(metric);
	}

	@Test
	public void helpsWithExceptionalPath() {
		// setup:
		InOrder inOrder = inOrder(answerFlow, stats, observer, answer);

		given(answerFlow.satisfyUsing(answer, query)).willThrow(IllegalStateException.class);
		given(answer.responseGiven(query, StateView.EMPTY_VIEW, FAIL_INVALID, 0L)).willReturn(notOkResponse);

		// when:
		subject.respondToCrypto(query, observer, answer, metric);

		// then:
		inOrder.verify(stats).cryptoQueryReceived(metric);
		inOrder.verify(answerFlow).satisfyUsing(answer, query);
		inOrder.verify(answer).responseGiven(query, StateView.EMPTY_VIEW, FAIL_INVALID, 0L);
		inOrder.verify(observer).onNext(notOkResponse);
		inOrder.verify(observer).onCompleted();
		inOrder.verify(stats, never()).cryptoQuerySubmitted(metric);
	}

	@Test
	public void helpsWithNetworkHappyPath() {
		// setup:
		InOrder inOrder = inOrder(answerFlow, stats, observer);

		given(answerFlow.satisfyUsing(answer, query)).willReturn(okResponse);
		given(answer.extractValidityFrom(okResponse)).willReturn(OK);

		// when:
		subject.respondToNetwork(query, observer, answer, metric);

		// then:
		inOrder.verify(stats).networkQueryReceived(metric);
		inOrder.verify(answerFlow).satisfyUsing(answer, query);
		inOrder.verify(observer).onNext(okResponse);
		inOrder.verify(observer).onCompleted();
		inOrder.verify(stats).networkQueryAnswered(metric);
	}
}
