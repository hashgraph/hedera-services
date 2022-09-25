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

import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetContents;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemUndelete;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

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

class FileControllerTest {
    Query query = Query.getDefaultInstance();
    Transaction txn = Transaction.getDefaultInstance();
    FileAnswers answers;
    TxnResponseHelper txnResponseHelper;
    QueryResponseHelper queryResponseHelper;
    StreamObserver<Response> queryObserver;
    StreamObserver<TransactionResponse> txnObserver;

    FileController subject;

    @BeforeEach
    void setup() {
        answers = mock(FileAnswers.class);
        txnObserver = mock(StreamObserver.class);
        queryObserver = mock(StreamObserver.class);

        txnResponseHelper = mock(TxnResponseHelper.class);
        queryResponseHelper = mock(QueryResponseHelper.class);

        subject = new FileController(answers, txnResponseHelper, queryResponseHelper);
    }

    @Test
    void forwardsUpdateAsExpected() {
        // when:
        subject.updateFile(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, FileUpdate);
    }

    @Test
    void forwardsCreateAsExpected() {
        // when:
        subject.createFile(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, FileCreate);
    }

    @Test
    void forwardsDeleteAsExpected() {
        // when:
        subject.deleteFile(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, FileDelete);
    }

    @Test
    void forwardsAppendAsExpected() {
        // when:
        subject.appendContent(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, FileAppend);
    }

    @Test
    void forwardsSysDelAsExpected() {
        // when:
        subject.systemDelete(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, SystemDelete);
    }

    @Test
    void forwardsSysUndelAsExpected() {
        // when:
        subject.systemUndelete(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, SystemUndelete);
    }

    @Test
    void forwardsFileInfoAsExpected() {
        // when:
        subject.getFileInfo(query, queryObserver);

        // expect:
        verify(answers).fileInfo();
        verify(queryResponseHelper).answer(query, queryObserver, null, FileGetInfo);
    }

    @Test
    void forwardsFileContentsAsExpected() {
        // when:
        subject.getFileContent(query, queryObserver);

        // expect:
        verify(answers).fileContents();
        verify(queryResponseHelper).answer(query, queryObserver, null, FileGetContents);
    }
}
