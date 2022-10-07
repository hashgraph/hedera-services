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

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusDeleteTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.queries.answering.QueryResponseHelper;
import com.hedera.services.queries.consensus.HcsAnswers;
import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsensusControllerTest {
    Query query = Query.getDefaultInstance();
    Transaction txn = Transaction.getDefaultInstance();
    HcsAnswers hcsAnswers;
    TxnResponseHelper txnResponseHelper;
    QueryResponseHelper queryResponseHelper;
    StreamObserver<Response> queryObserver;
    StreamObserver<TransactionResponse> txnObserver;

    ConsensusController subject;

    @BeforeEach
    void setup() {
        txnObserver = mock(StreamObserver.class);
        queryObserver = mock(StreamObserver.class);

        hcsAnswers = mock(HcsAnswers.class);
        txnResponseHelper = mock(TxnResponseHelper.class);
        queryResponseHelper = mock(QueryResponseHelper.class);

        subject = new ConsensusController(hcsAnswers, txnResponseHelper, queryResponseHelper);
    }

    @Test
    void forwardsTopicInfoAsExpected() {
        // when:
        subject.getTopicInfo(query, queryObserver);

        // expect:
        verify(hcsAnswers).topicInfo();
        verify(queryResponseHelper)
                .answer(query, queryObserver, null, HederaFunctionality.ConsensusGetTopicInfo);
    }

    @Test
    void forwardsCreateAsExpected() {
        // when:
        subject.createTopic(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, ConsensusCreateTopic);
    }

    @Test
    void forwardsDeleteAsExpected() {
        // when:
        subject.deleteTopic(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, ConsensusDeleteTopic);
    }

    @Test
    void forwardsUpdateAsExpected() {
        // when:
        subject.updateTopic(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, ConsensusUpdateTopic);
    }

    @Test
    void forwardsSubmitAsExpected() {
        // when:
        subject.submitMessage(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, ConsensusSubmitMessage);
    }
}
