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
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;

import com.hedera.services.queries.answering.QueryResponseHelper;
import com.hedera.services.queries.consensus.HcsAnswers;
import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.ConsensusServiceGrpc;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ConsensusController extends ConsensusServiceGrpc.ConsensusServiceImplBase {
    private final HcsAnswers hcsAnswers;
    private final TxnResponseHelper txnHelper;
    private final QueryResponseHelper queryHelper;

    public static final String GET_TOPIC_INFO_METRIC = "getTopicInfo";
    public static final String CREATE_TOPIC_METRIC = "createTopic";
    public static final String UPDATE_TOPIC_METRIC = "updateTopic";
    public static final String DELETE_TOPIC_METRIC = "deleteTopic";
    public static final String SUBMIT_MESSAGE_METRIC = "submitMessage";

    @Inject
    public ConsensusController(
            HcsAnswers hcsAnswers, TxnResponseHelper txnHelper, QueryResponseHelper queryHelper) {
        this.hcsAnswers = hcsAnswers;
        this.txnHelper = txnHelper;
        this.queryHelper = queryHelper;
    }

    @Override
    public void getTopicInfo(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(query, observer, hcsAnswers.topicInfo(), ConsensusGetTopicInfo);
    }

    @Override
    public void createTopic(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, ConsensusCreateTopic);
    }

    @Override
    public void updateTopic(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, ConsensusUpdateTopic);
    }

    @Override
    public void deleteTopic(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, ConsensusDeleteTopic);
    }

    @Override
    public void submitMessage(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, ConsensusSubmitMessage);
    }
}
