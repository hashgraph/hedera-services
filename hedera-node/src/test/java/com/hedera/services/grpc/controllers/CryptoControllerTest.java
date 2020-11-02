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
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoAddLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountBalance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountRecords;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetStakers;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NONE;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetReceipt;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetRecord;
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
		verify(queryResponseHelper).answer(query, queryObserver, null, CryptoGetInfo);
	}

	@Test
	public void forwardsGetBalanceAsExpected() {
		// when:
		subject.cryptoGetBalance(query, queryObserver);

		// expect:
		verify(cryptoAnswers).getAccountBalance();
		verify(queryResponseHelper).answer(query, queryObserver, null, CryptoGetAccountBalance);
	}

	@Test
	public void forwardsGetRecordsAsExpected() {
		// when:
		subject.getAccountRecords(query, queryObserver);

		// expect:
		verify(cryptoAnswers).getAccountRecords();
		verify(queryResponseHelper).answer(query, queryObserver, null, CryptoGetAccountRecords);
	}

	@Test
	public void forwardsGetStakersAsExpected() {
		// when:
		subject.getStakersByAccountID(query, queryObserver);

		// expect:
		verify(cryptoAnswers).getStakers();
		verify(queryResponseHelper).answer(query, queryObserver, null, CryptoGetStakers);
	}

	@Test
	public void forwardsGetLiveHashAsExpected() {
		// when:
		subject.getLiveHash(query, queryObserver);

		// expect:
		verify(cryptoAnswers).getLiveHash();
		verify(queryResponseHelper).answer(query, queryObserver, null, CryptoGetLiveHash);
	}

	@Test
	public void forwardsGetReceiptAsExpected() {
		// when:
		subject.getTransactionReceipts(query, queryObserver);

		// expect:
		verify(metaAnswers).getTxnReceipt();
		verify(queryResponseHelper).answer(query, queryObserver, null, TransactionGetReceipt);
	}

	@Test
	public void forwardsGetRecordAsExpected() {
		// when:
		subject.getTxRecordByTxID(query, queryObserver);

		// expect:
		verify(metaAnswers).getTxnRecord();
		verify(queryResponseHelper).answer(query, queryObserver, null, TransactionGetRecord);
	}

	@Test
	public void forwardsGetFastRecordAsExpected() {
		// when:
		subject.getFastTransactionRecord(query, queryObserver);

		// expect:
		verify(metaAnswers).getFastTxnRecord();
		verify(queryResponseHelper).answer(query, queryObserver, null, NONE);
	}

	@Test
	public void forwardsTransferAsExpected() {
		// when:
		subject.cryptoTransfer(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, CryptoTransfer);
	}

	@Test
	public void forwardsCreateAsExpected() {
		// when:
		subject.createAccount(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, CryptoCreate);
	}

	@Test
	public void forwardsDeleteAsExpected() {
		// when:
		subject.cryptoDelete(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, CryptoDelete);
	}

	@Test
	public void forwardsUpdateAsExpected() {
		// when:
		subject.updateAccount(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, CryptoUpdate);
	}

	@Test
	public void forwardsAddLiveHashAsExpected() {
		// when:
		subject.addLiveHash(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, CryptoAddLiveHash);
	}

	@Test
	public void forwardsDeleteLiveHashAsExpected() {
		// when:
		subject.deleteLiveHash(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, CryptoDeleteLiveHash);
	}
}
