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
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.*;
import static com.hedera.services.grpc.controllers.CryptoController.*;

@RunWith(JUnitPlatform.class)
class CryptoControllerTest {
	Query query = Query.getDefaultInstance();
	Transaction txn = Transaction.getDefaultInstance();
	MetaAnswers metaAnswers;
	CryptoAnswers cryptoAnswers;
	TxnResponseHelper txnResponseHelper;
	QueryResponseHelper queryResponseHelper;
	StreamObserver<Response> queryObserver;
	StreamObserver<TransactionResponse> txnObserver;

	CryptoController subject;

	@BeforeEach
	private void setup() {
		txnObserver = mock(StreamObserver.class);
		queryObserver = mock(StreamObserver.class);

		metaAnswers = mock(MetaAnswers.class);
		cryptoAnswers = mock(CryptoAnswers.class);
		txnResponseHelper = mock(TxnResponseHelper.class);
		queryResponseHelper = mock(QueryResponseHelper.class);

		subject = new CryptoController(metaAnswers, cryptoAnswers, txnResponseHelper, queryResponseHelper);
	}

	@Test
	public void forwardsAccountInfoAsExpected() {
		// when:
		subject.getAccountInfo(query, queryObserver);

		// expect:
		verify(cryptoAnswers).getAccountInfo();
		verify(queryResponseHelper).respondToCrypto(query, queryObserver, null, GET_ACCOUNT_INFO_METRIC);
	}

	@Test
	public void forwardsGetBalanceAsExpected() {
		// when:
		subject.cryptoGetBalance(query, queryObserver);

		// expect:
		verify(cryptoAnswers).getAccountBalance();
		verify(queryResponseHelper).respondToCrypto(query, queryObserver, null, GET_ACCOUNT_BALANCE_METRIC);
	}

	@Test
	public void forwardsGetRecordsAsExpected() {
		// when:
		subject.getAccountRecords(query, queryObserver);

		// expect:
		verify(cryptoAnswers).getAccountRecords();
		verify(queryResponseHelper).respondToCrypto(query, queryObserver, null, GET_ACCOUNT_RECORDS_METRIC);
	}

	@Test
	public void forwardsGetStakersAsExpected() {
		// when:
		subject.getStakersByAccountID(query, queryObserver);

		// expect:
		verify(cryptoAnswers).getStakers();
		verify(queryResponseHelper).respondToCrypto(query, queryObserver, null, GET_STAKERS_METRIC);
	}

	@Test
	public void forwardsGetLiveHashAsExpected() {
		// when:
		subject.getLiveHash(query, queryObserver);

		// expect:
		verify(cryptoAnswers).getLiveHash();
		verify(queryResponseHelper).respondToCrypto(query, queryObserver, null, GET_CLAIM_METRIC);
	}

	@Test
	public void forwardsGetReceiptAsExpected() {
		// when:
		subject.getTransactionReceipts(query, queryObserver);

		// expect:
		verify(metaAnswers).getTxnReceipt();
		verify(queryResponseHelper).respondToCrypto(query, queryObserver, null, GET_RECEIPT_METRIC);
	}

	@Test
	public void forwardsGetRecordAsExpected() {
		// when:
		subject.getTxRecordByTxID(query, queryObserver);

		// expect:
		verify(metaAnswers).getTxnRecord();
		verify(queryResponseHelper).respondToCrypto(query, queryObserver, null, GET_RECORD_METRIC);
	}

	@Test
	public void forwardsGetFastRecordAsExpected() {
		// when:
		subject.getFastTransactionRecord(query, queryObserver);

		// expect:
		verify(metaAnswers).getFastTxnRecord();
		verify(queryResponseHelper).respondToCrypto(query, queryObserver, null, GET_FAST_RECORD_METRIC);
	}

	@Test
	public void forwardsTransferAsExpected() {
		// when:
		subject.cryptoTransfer(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToCrypto(txn, txnObserver, CRYPTO_TRANSFER_METRIC);
	}

	@Test
	public void forwardsCreateAsExpected() {
		// when:
		subject.createAccount(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToCrypto(txn, txnObserver, CRYPTO_CREATE_METRIC);
	}

	@Test
	public void forwardsDeleteAsExpected() {
		// when:
		subject.cryptoDelete(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToCrypto(txn, txnObserver, CRYPTO_DELETE_METRIC);
	}

	@Test
	public void forwardsUpdateAsExpected() {
		// when:
		subject.updateAccount(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToCrypto(txn, txnObserver, CRYPTO_UPDATE_METRIC);
	}

	@Test
	public void forwardsAddLiveHashAsExpected() {
		// when:
		subject.addLiveHash(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToCrypto(txn, txnObserver, ADD_LIVE_HASH_METRIC);
	}

	@Test
	public void forwardsDeleteLiveHashAsExpected() {
		// when:
		subject.deleteLiveHash(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToCrypto(txn, txnObserver, DELETE_LIVE_HASH_METRIC);
	}
}
