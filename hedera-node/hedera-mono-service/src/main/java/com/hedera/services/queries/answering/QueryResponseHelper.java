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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.queries.AnswerFlow;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.stats.HapiOpCounters;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

@Singleton
public class QueryResponseHelper {
    private static final Logger log = LogManager.getLogger(QueryResponseHelper.class);
    private static final Marker ALL_QUERIES_MARKER = MarkerManager.getMarker("ALL_QUERIES");

    private final AnswerFlow answerFlow;
    private final HapiOpCounters opCounters;

    @Inject
    public QueryResponseHelper(AnswerFlow answerFlow, HapiOpCounters opCounters) {
        this.opCounters = opCounters;
        this.answerFlow = answerFlow;
    }

    public void answer(
            Query query,
            StreamObserver<Response> observer,
            AnswerService answer,
            HederaFunctionality statedFunction) {
        respondWithMetrics(
                query,
                observer,
                answer,
                () -> opCounters.countReceived(statedFunction),
                () -> opCounters.countAnswered(statedFunction));
    }

    private void respondWithMetrics(
            Query query,
            StreamObserver<Response> observer,
            AnswerService answer,
            Runnable incReceivedCount,
            Runnable incAnsweredCount) {
        if (log.isDebugEnabled()) {
            log.debug(ALL_QUERIES_MARKER, "Received query: {}", query);
        }
        Response response;
        incReceivedCount.run();

        try {
            response = answerFlow.satisfyUsing(answer, query);
        } catch (Exception surprising) {
            log.warn("Query flow unable to satisfy query {}!", query, surprising);
            response = answer.responseGiven(query, null, FAIL_INVALID, 0L);
        }

        observer.onNext(response);
        observer.onCompleted();

        if (answer.extractValidityFrom(response) == OK) {
            incAnsweredCount.run();
        }
    }
}
