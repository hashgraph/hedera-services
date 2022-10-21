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
package com.hedera.services.grpc.controllers;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleSign;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.services.queries.answering.QueryResponseHelper;
import com.hedera.services.queries.schedule.ScheduleAnswers;
import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleControllerTest {
    Query query = Query.getDefaultInstance();
    Transaction txn = Transaction.getDefaultInstance();

    ScheduleAnswers answers;
    TxnResponseHelper txnResponseHelper;
    QueryResponseHelper queryResponseHelper;
    StreamObserver<Response> queryObserver;
    StreamObserver<TransactionResponse> txnObserver;

    ScheduleController subject;

    @BeforeEach
    void setup() {
        answers = mock(ScheduleAnswers.class);
        txnObserver = mock(StreamObserver.class);
        queryObserver = mock(StreamObserver.class);

        txnResponseHelper = mock(TxnResponseHelper.class);
        queryResponseHelper = mock(QueryResponseHelper.class);

        subject = new ScheduleController(answers, txnResponseHelper, queryResponseHelper);
    }

    @Test
    void forwardScheduleCreateAsExpected() {
        // when:
        subject.createSchedule(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, ScheduleCreate);
    }

    @Test
    void forwardScheduleDeleteAsExpected() {
        // when:
        subject.deleteSchedule(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, ScheduleDelete);
    }

    @Test
    void forwardScheduleSignAsExpected() {
        // when:
        subject.signSchedule(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, ScheduleSign);
    }

    @Test
    void forwardsScheduleInfoAsExpected() {
        // when:
        subject.getScheduleInfo(query, queryObserver);

        // expect:
        verify(queryResponseHelper).answer(query, queryObserver, null, ScheduleGetInfo);
    }
}
