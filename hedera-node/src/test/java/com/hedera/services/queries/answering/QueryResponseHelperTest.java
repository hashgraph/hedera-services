/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.queries.answering;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_START;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;

import com.hedera.services.queries.AnswerFlow;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.stats.HapiOpCounters;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class QueryResponseHelperTest {
    Query query = Query.getDefaultInstance();
    String metric = "imaginary";
    Response okResponse;
    Response notOkResponse;

    AnswerFlow answerFlow;
    AnswerService answer;
    HapiOpCounters opCounters;
    StreamObserver<Response> observer;

    QueryResponseHelper subject;

    @BeforeEach
    void setup() {
        answerFlow = mock(AnswerFlow.class);
        opCounters = mock(HapiOpCounters.class);
        answer = mock(AnswerService.class);
        observer = mock(StreamObserver.class);
        okResponse = mock(Response.class);
        notOkResponse = mock(Response.class);

        subject = new QueryResponseHelper(answerFlow, opCounters);
    }

    @Test
    void helpsWithAnswerHappyPath() {
        // setup:
        InOrder inOrder = inOrder(answerFlow, opCounters, observer);

        given(answerFlow.satisfyUsing(answer, query)).willReturn(okResponse);
        given(answer.extractValidityFrom(okResponse)).willReturn(OK);

        // when:
        subject.answer(query, observer, answer, TokenGetInfo);

        // then:
        inOrder.verify(opCounters).countReceived(TokenGetInfo);
        inOrder.verify(answerFlow).satisfyUsing(answer, query);
        inOrder.verify(observer).onNext(okResponse);
        inOrder.verify(observer).onCompleted();
        inOrder.verify(opCounters).countAnswered(TokenGetInfo);
    }

    @Test
    void helpsWithAnswerUnhappyPath() {
        // setup:
        InOrder inOrder = inOrder(answerFlow, opCounters, observer);

        given(answerFlow.satisfyUsing(answer, query)).willReturn(notOkResponse);
        given(answer.extractValidityFrom(okResponse)).willReturn(INVALID_TRANSACTION_START);

        // when:
        subject.answer(query, observer, answer, TokenGetInfo);

        // then:
        inOrder.verify(opCounters).countReceived(TokenGetInfo);
        inOrder.verify(answerFlow).satisfyUsing(answer, query);
        inOrder.verify(observer).onNext(notOkResponse);
        inOrder.verify(observer).onCompleted();
        inOrder.verify(opCounters, never()).countAnswered(TokenGetInfo);
    }
}
