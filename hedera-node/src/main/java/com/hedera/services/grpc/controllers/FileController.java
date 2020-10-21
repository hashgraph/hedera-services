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
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import io.grpc.stub.StreamObserver;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetContents;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetInfo;

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
	public static final String FILE_SYSDEL_METRIC = "fileSystemDelete";
	public static final String FILE_SYSUNDEL_METRIC = "fileSystemUndelete";

	public FileController(
			FileAnswers fileAnswers,
			TxnResponseHelper txnHelper,
			QueryResponseHelper queryHelper
	) {
		this.txnHelper = txnHelper;
		this.queryHelper = queryHelper;
		this.fileAnswers = fileAnswers;
	}

	@Override
	public void updateFile(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToFile(signedTxn, observer, UPDATE_FILE_METRIC);
	}

	@Override
	public void createFile(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToFile(signedTxn, observer, CREATE_FILE_METRIC);
	}

	@Override
	public void deleteFile(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToFile(signedTxn, observer, DELETE_FILE_METRIC);
	}

	@Override
	public void appendContent(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToFile(signedTxn, observer, FILE_APPEND_METRIC);
	}

	@Override
	public void systemDelete(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToFile(signedTxn, observer, FILE_SYSDEL_METRIC);
	}

	@Override
	public void systemUndelete(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToFile(signedTxn, observer, FILE_SYSUNDEL_METRIC);
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
