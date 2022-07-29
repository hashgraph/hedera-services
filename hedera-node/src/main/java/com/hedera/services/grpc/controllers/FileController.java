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

import com.hedera.services.queries.answering.QueryResponseHelper;
import com.hedera.services.queries.file.FileAnswers;
import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FileController extends FileServiceGrpc.FileServiceImplBase {
    private final FileAnswers fileAnswers;
    private final TxnResponseHelper txnHelper;
    private final QueryResponseHelper queryHelper;

    public static final String GET_FILE_INFO_METRIC = "getFileInfo";
    public static final String GET_FILE_CONTENT_METRIC = "getFileContent";
    public static final String UPDATE_FILE_METRIC = "updateFile";
    public static final String CREATE_FILE_METRIC = "createFile";
    public static final String DELETE_FILE_METRIC = "deleteFile";
    public static final String FILE_APPEND_METRIC = "appendContent";

    @Inject
    public FileController(
            FileAnswers fileAnswers, TxnResponseHelper txnHelper, QueryResponseHelper queryHelper) {
        this.txnHelper = txnHelper;
        this.queryHelper = queryHelper;
        this.fileAnswers = fileAnswers;
    }

    @Override
    public void updateFile(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, FileUpdate);
    }

    @Override
    public void createFile(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, FileCreate);
    }

    @Override
    public void deleteFile(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, FileDelete);
    }

    @Override
    public void appendContent(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, FileAppend);
    }

    @Override
    public void systemDelete(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, SystemDelete);
    }

    @Override
    public void systemUndelete(
            Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, SystemUndelete);
    }

    @Override
    public void getFileInfo(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(query, observer, fileAnswers.fileInfo(), FileGetInfo);
    }

    @Override
    public void getFileContent(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(query, observer, fileAnswers.fileContents(), FileGetContents);
    }
}
