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
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
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
	private void setup() {
		txnObserver = mock(StreamObserver.class);
		queryObserver = mock(StreamObserver.class);

		hcsAnswers = mock(HcsAnswers.class);
		txnResponseHelper = mock(TxnResponseHelper.class);
		queryResponseHelper = mock(QueryResponseHelper.class);

		subject = new ConsensusController(hcsAnswers, txnResponseHelper, queryResponseHelper);
	}

	@Test
	public void forwardsTopicInfoAsExpected() {
		// when:
		subject.getTopicInfo(query, queryObserver);

		// expect:
		verify(hcsAnswers).topicInfo();
		verify(queryResponseHelper).respondToHcs(query, queryObserver, null, ConsensusController.GET_TOPIC_INFO_METRIC);
	}

	@Test
	public void forwardsCreateAsExpected() {
		// when:
		subject.createTopic(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToHcs(txn, txnObserver, ConsensusController.CREATE_TOPIC_METRIC);
	}

	@Test
	public void forwardsDeleteAsExpected() {
		// when:
		subject.deleteTopic(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToHcs(txn, txnObserver, ConsensusController.DELETE_TOPIC_METRIC);
	}

	@Test
	public void forwardsUpdateAsExpected() {
		// when:
		subject.updateTopic(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToHcs(txn, txnObserver, ConsensusController.UPDATE_TOPIC_METRIC);
	}

	@Test
	public void forwardsSubmitAsExpected() {
		// when:
		subject.submitMessage(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToHcs(txn, txnObserver, ConsensusController.SUBMIT_MESSAGE_METRIC);
	}
}
