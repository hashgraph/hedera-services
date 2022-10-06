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

import static com.hederahashgraph.api.proto.java.HederaFunctionality.NetworkGetExecutionTime;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.queries.answering.QueryResponseHelper;
import com.hedera.services.queries.meta.MetaAnswers;
import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NetworkControllerTest {
    Query query = Query.getDefaultInstance();
    Transaction txn = Transaction.getDefaultInstance();
    MetaAnswers answers;
    TxnResponseHelper txnResponseHelper;
    QueryResponseHelper queryResponseHelper;
    StreamObserver<Response> queryObserver;
    StreamObserver<TransactionResponse> txnObserver;

    NetworkController subject;

    @BeforeEach
    void setup() {
        answers = mock(MetaAnswers.class);
        txnObserver = mock(StreamObserver.class);
        queryObserver = mock(StreamObserver.class);

        txnResponseHelper = mock(TxnResponseHelper.class);
        queryResponseHelper = mock(QueryResponseHelper.class);

        subject = new NetworkController(answers, txnResponseHelper, queryResponseHelper);
    }

    @Test
    void forwardsVersionInfoAsExpected() {
        // when:
        subject.getVersionInfo(query, queryObserver);

        // expect:
        verify(answers).getVersionInfo();
        verify(queryResponseHelper)
                .answer(query, queryObserver, null, HederaFunctionality.GetVersionInfo);
    }

    @Test
    void forwardsGetExecTimeAsExpected() {
        // when:
        subject.getExecutionTime(query, queryObserver);

        // expect:
        verify(answers).getExecTime();
        verify(queryResponseHelper).answer(query, queryObserver, null, NetworkGetExecutionTime);
    }

    @Test
    void forwardsUncheckedSubmitAsExpected() {
        // when:
        subject.uncheckedSubmit(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, HederaFunctionality.UncheckedSubmit);
    }

    @Test
    void forwardsAccountDetailsAsExpected() {
        // when:
        subject.getAccountDetails(query, queryObserver);

        // expect:
        verify(answers).getAccountDetails();
        verify(queryResponseHelper)
                .answer(query, queryObserver, null, HederaFunctionality.GetAccountDetails);
    }
}
