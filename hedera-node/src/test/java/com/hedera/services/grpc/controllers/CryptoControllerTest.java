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

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoAddLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoApproveAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteAllowance;
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
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

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
    void setup() {
        txnObserver = mock(StreamObserver.class);
        queryObserver = mock(StreamObserver.class);

        metaAnswers = mock(MetaAnswers.class);
        cryptoAnswers = mock(CryptoAnswers.class);
        txnResponseHelper = mock(TxnResponseHelper.class);
        queryResponseHelper = mock(QueryResponseHelper.class);

        subject =
                new CryptoController(
                        metaAnswers, cryptoAnswers, txnResponseHelper, queryResponseHelper);
    }

    @Test
    void forwardsAccountInfoAsExpected() {
        // when:
        subject.getAccountInfo(query, queryObserver);

        // expect:
        verify(cryptoAnswers).getAccountInfo();
        verify(queryResponseHelper).answer(query, queryObserver, null, CryptoGetInfo);
    }

    @Test
    void forwardsGetBalanceAsExpected() {
        // when:
        subject.cryptoGetBalance(query, queryObserver);

        // expect:
        verify(cryptoAnswers).getAccountBalance();
        verify(queryResponseHelper).answer(query, queryObserver, null, CryptoGetAccountBalance);
    }

    @Test
    void forwardsGetRecordsAsExpected() {
        // when:
        subject.getAccountRecords(query, queryObserver);

        // expect:
        verify(cryptoAnswers).getAccountRecords();
        verify(queryResponseHelper).answer(query, queryObserver, null, CryptoGetAccountRecords);
    }

    @Test
    void forwardsGetStakersAsExpected() {
        // when:
        subject.getStakersByAccountID(query, queryObserver);

        // expect:
        verify(cryptoAnswers).getStakers();
        verify(queryResponseHelper).answer(query, queryObserver, null, CryptoGetStakers);
    }

    @Test
    void forwardsGetLiveHashAsExpected() {
        // when:
        subject.getLiveHash(query, queryObserver);

        // expect:
        verify(cryptoAnswers).getLiveHash();
        verify(queryResponseHelper).answer(query, queryObserver, null, CryptoGetLiveHash);
    }

    @Test
    void forwardsGetReceiptAsExpected() {
        // when:
        subject.getTransactionReceipts(query, queryObserver);

        // expect:
        verify(metaAnswers).getTxnReceipt();
        verify(queryResponseHelper).answer(query, queryObserver, null, TransactionGetReceipt);
    }

    @Test
    void forwardsGetRecordAsExpected() {
        // when:
        subject.getTxRecordByTxID(query, queryObserver);

        // expect:
        verify(metaAnswers).getTxnRecord();
        verify(queryResponseHelper).answer(query, queryObserver, null, TransactionGetRecord);
    }

    @Test
    void forwardsGetFastRecordAsExpected() {
        // when:
        subject.getFastTransactionRecord(query, queryObserver);

        // expect:
        verify(metaAnswers).getFastTxnRecord();
        verify(queryResponseHelper).answer(query, queryObserver, null, NONE);
    }

    @Test
    void forwardsTransferAsExpected() {
        // when:
        subject.cryptoTransfer(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, CryptoTransfer);
    }

    @Test
    void forwardsCreateAsExpected() {
        // when:
        subject.createAccount(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, CryptoCreate);
    }

    @Test
    void forwardsDeleteAsExpected() {
        // when:
        subject.cryptoDelete(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, CryptoDelete);
    }

    @Test
    void forwardsUpdateAsExpected() {
        // when:
        subject.updateAccount(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, CryptoUpdate);
    }

    @Test
    void forwardsAddLiveHashAsExpected() {
        // when:
        subject.addLiveHash(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, CryptoAddLiveHash);
    }

    @Test
    void forwardsDeleteLiveHashAsExpected() {
        // when:
        subject.deleteLiveHash(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, CryptoDeleteLiveHash);
    }

    @Test
    void forwardsApproveAsExpected() {
        // when:
        subject.approveAllowances(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, CryptoApproveAllowance);
    }

    @Test
    void forwardsDeleteAllowanceAsExpected() {
        // when:
        subject.deleteAllowances(txn, txnObserver);

        // expect:
        verify(txnResponseHelper).submit(txn, txnObserver, CryptoDeleteAllowance);
    }
}
