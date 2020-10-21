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
import com.hedera.services.queries.crypto.CryptoAnswers;
import com.hedera.services.queries.meta.MetaAnswers;
import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CryptoController extends CryptoServiceGrpc.CryptoServiceImplBase {
	private static final Logger log = LogManager.getLogger(CryptoController.class);

	public static final String GET_ACCOUNT_INFO_METRIC = "getAccountInfo";
	public static final String GET_ACCOUNT_BALANCE_METRIC = "cryptoGetBalance";
	public static final String GET_ACCOUNT_RECORDS_METRIC = "getAccountRecords";
	public static final String GET_STAKERS_METRIC = "getStakersByAccountID";
	public static final String GET_LIVE_HASH_METRIC = "getClaim";
	public static final String GET_RECEIPT_METRIC = "getTransactionReceipts";
	public static final String GET_RECORD_METRIC = "getTxRecordByTxID";
	public static final String GET_FAST_RECORD_METRIC = "getFastTransactionRecord";
	public static final String CRYPTO_TRANSFER_METRIC = "cryptoTransfer";
	public static final String CRYPTO_DELETE_METRIC = "cryptoDelete";
	public static final String CRYPTO_CREATE_METRIC = "createAccount";
	public static final String CRYPTO_UPDATE_METRIC = "updateAccount";
	public static final String ADD_LIVE_HASH_METRIC = "addLiveHash";
	public static final String DELETE_LIVE_HASH_METRIC = "deleteLiveHash";

	private final MetaAnswers metaAnswers;
	private final CryptoAnswers cryptoAnswers;
	private final TxnResponseHelper txnHelper;
	private final QueryResponseHelper queryHelper;

	public CryptoController(
			MetaAnswers metaAnswers,
			CryptoAnswers cryptoAnswers,
			TxnResponseHelper txnHelper,
			QueryResponseHelper queryHelper
	) {
		this.metaAnswers = metaAnswers;
		this.cryptoAnswers = cryptoAnswers;
		this.txnHelper = txnHelper;
		this.queryHelper = queryHelper;
	}

	@Override
	public void getAccountInfo(Query query, StreamObserver<Response> observer) {
		queryHelper.respondToCrypto(query, observer, cryptoAnswers.getAccountInfo(), GET_ACCOUNT_INFO_METRIC);
	}

	@Override
	public void cryptoGetBalance(Query query, StreamObserver<Response> observer) {
		queryHelper.respondToCrypto(query, observer, cryptoAnswers.getAccountBalance(), GET_ACCOUNT_BALANCE_METRIC);
	}

	@Override
	public void getAccountRecords(Query query, StreamObserver<Response> observer) {
		queryHelper.respondToCrypto(query, observer, cryptoAnswers.getAccountRecords(), GET_ACCOUNT_RECORDS_METRIC);
	}

	@Override
	public void getStakersByAccountID(Query query, StreamObserver<Response> observer) {
		queryHelper.respondToCrypto(query, observer, cryptoAnswers.getStakers(), GET_STAKERS_METRIC);
	}

	@Override
	public void getLiveHash(Query query, StreamObserver<Response> observer) {
		queryHelper.respondToCrypto(query, observer, cryptoAnswers.getLiveHash(), GET_LIVE_HASH_METRIC);
	}

	@Override
	public void getTransactionReceipts(Query query, StreamObserver<Response> observer) {
		queryHelper.respondToCrypto(query, observer, metaAnswers.getTxnReceipt(), GET_RECEIPT_METRIC);
	}

	@Override
	public void getTxRecordByTxID(Query query, StreamObserver<Response> observer) {
		queryHelper.respondToCrypto(query, observer, metaAnswers.getTxnRecord(), GET_RECORD_METRIC);
	}

	@Override
	public void getFastTransactionRecord(Query query, StreamObserver<Response> observer) {
		queryHelper.respondToCrypto(query, observer, metaAnswers.getFastTxnRecord(), GET_FAST_RECORD_METRIC);
	}

	@Override
	public void cryptoTransfer(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToCrypto(signedTxn, observer, CRYPTO_TRANSFER_METRIC);
	}

	@Override
	public void cryptoDelete(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToCrypto(signedTxn, observer, CRYPTO_DELETE_METRIC);
	}

	@Override
	public void createAccount(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToCrypto(signedTxn, observer, CRYPTO_CREATE_METRIC);
	}

	@Override
	public void updateAccount(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToCrypto(signedTxn, observer, CRYPTO_UPDATE_METRIC);
	}

	@Override
	public void addLiveHash(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToCrypto(signedTxn, observer, ADD_LIVE_HASH_METRIC);
	}

	@Override
	public void deleteLiveHash(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToCrypto(signedTxn, observer, DELETE_LIVE_HASH_METRIC);
	}
}
