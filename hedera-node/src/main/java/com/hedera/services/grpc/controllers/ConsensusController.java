package com.hedera.services.grpc.controllers;

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

import com.hedera.services.queries.answering.QueryResponseHelper;
import com.hedera.services.queries.consensus.HcsAnswers;
import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.ConsensusServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static com.hedera.services.legacy.services.stats.HederaNodeStats.GET_TOPIC_INFO_COUNT;
import static com.hedera.services.legacy.services.stats.HederaNodeStats.CREATE_TOPIC_COUNT;
import static com.hedera.services.legacy.services.stats.HederaNodeStats.UPDATE_TOPIC_COUNT;
import static com.hedera.services.legacy.services.stats.HederaNodeStats.DELETE_TOPIC_COUNT;
import static com.hedera.services.legacy.services.stats.HederaNodeStats.SUBMIT_MESSAGE_COUNT;


public class ConsensusController extends ConsensusServiceGrpc.ConsensusServiceImplBase {
	private static final Logger log = LogManager.getLogger(ConsensusController.class);

	private final HcsAnswers hcsAnswers;
	private final TxnResponseHelper txnHelper;
	private final QueryResponseHelper queryHelper;

	public ConsensusController(
			HcsAnswers hcsAnswers,
			TxnResponseHelper txnHelper,
			QueryResponseHelper queryHelper
	) {
		this.hcsAnswers = hcsAnswers;
		this.txnHelper = txnHelper;
		this.queryHelper = queryHelper;
	}

	@Override
	public void getTopicInfo(Query query, StreamObserver<Response> observer) {
		queryHelper.respondToHcs(query, observer, hcsAnswers.topicInfo(), GET_TOPIC_INFO_COUNT);
	}

	@Override
	public void createTopic(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToHcs(signedTxn, observer, CREATE_TOPIC_COUNT);
	}

	@Override
	public void updateTopic(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToHcs(signedTxn, observer, UPDATE_TOPIC_COUNT);
	}

	@Override
	public void deleteTopic(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToHcs(signedTxn, observer, DELETE_TOPIC_COUNT);
	}

	@Override
	public void submitMessage(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToHcs(signedTxn, observer, SUBMIT_MESSAGE_COUNT);
	}
}
