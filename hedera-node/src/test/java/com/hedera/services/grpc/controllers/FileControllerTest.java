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
import com.hedera.services.queries.file.FileAnswers;
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
import static com.hedera.services.grpc.controllers.FileController.*;

@RunWith(JUnitPlatform.class)
public class FileControllerTest {
	Query query = Query.getDefaultInstance();
	Transaction txn = Transaction.getDefaultInstance();
	FileAnswers answers;
	TxnResponseHelper txnResponseHelper;
	QueryResponseHelper queryResponseHelper;
	StreamObserver<Response> queryObserver;
	StreamObserver<TransactionResponse> txnObserver;

	FileController subject;

	@BeforeEach
	private void setup() {
		answers = mock(FileAnswers.class);
		txnObserver = mock(StreamObserver.class);
		queryObserver = mock(StreamObserver.class);

		txnResponseHelper = mock(TxnResponseHelper.class);
		queryResponseHelper = mock(QueryResponseHelper.class);

		subject = new FileController(answers, txnResponseHelper, queryResponseHelper);
	}

	@Test
	public void forwardsUpdateAsExpected() {
		// when:
		subject.updateFile(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToFile(txn, txnObserver, UPDATE_FILE_METRIC);
	}

	@Test
	public void forwardsCreateAsExpected() {
		// when:
		subject.createFile(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToFile(txn, txnObserver, CREATE_FILE_METRIC);
	}

	@Test
	public void forwardsDeleteAsExpected() {
		// when:
		subject.deleteFile(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToFile(txn, txnObserver, DELETE_FILE_METRIC);
	}

	@Test
	public void forwardsAppendAsExpected() {
		// when:
		subject.appendContent(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToFile(txn, txnObserver, APPEND_METRIC);
	}

	@Test
	public void forwardsSysDelAsExpected() {
		// when:
		subject.systemDelete(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToFile(txn, txnObserver, FILE_SYSDEL_METRIC);
	}

	@Test
	public void forwardsSysUndelAsExpected() {
		// when:
		subject.systemUndelete(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToFile(txn, txnObserver, FILE_SYSUNDEL_METRIC);
	}

	@Test
	public void forwardsFileInfoAsExpected() {
		// when:
		subject.getFileInfo(query, queryObserver);

		// expect:
		verify(answers).fileInfo();
		verify(queryResponseHelper).respondToFile(query, queryObserver, null, GET_FILE_INFO_METRIC);
	}

	@Test
	public void forwardsFileContentsAsExpected() {
		// when:
		subject.getFileContent(query, queryObserver);

		// expect:
		verify(answers).fileContents();
		verify(queryResponseHelper).respondToFile(query, queryObserver, null, GET_FILE_CONTENT_METRIC);
	}
}
