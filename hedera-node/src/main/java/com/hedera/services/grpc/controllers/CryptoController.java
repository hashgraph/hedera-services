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
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CryptoController extends CryptoServiceGrpc.CryptoServiceImplBase {
    public static final String GET_ACCOUNT_INFO_METRIC = "getAccountInfo";
    public static final String GET_ACCOUNT_BALANCE_METRIC = "cryptoGetBalance";
    public static final String GET_ACCOUNT_RECORDS_METRIC = "getAccountRecords";
    public static final String GET_LIVE_HASH_METRIC = "getClaim";
    public static final String GET_RECEIPT_METRIC = "getTransactionReceipts";
    public static final String GET_RECORD_METRIC = "getTxRecordByTxID";
    public static final String CRYPTO_TRANSFER_METRIC = "cryptoTransfer";
    public static final String CRYPTO_DELETE_METRIC = "cryptoDelete";
    public static final String CRYPTO_CREATE_METRIC = "createAccount";
    public static final String CRYPTO_UPDATE_METRIC = "updateAccount";
    public static final String ADD_LIVE_HASH_METRIC = "addLiveHash";
    public static final String DELETE_LIVE_HASH_METRIC = "deleteLiveHash";
    public static final String CRYPTO_APPROVE_ALLOWANCES = "approveAllowances";
    public static final String CRYPTO_DELETE_ALLOWANCE = "deleteAllowances";

    private final MetaAnswers metaAnswers;
    private final CryptoAnswers cryptoAnswers;
    private final TxnResponseHelper txnHelper;
    private final QueryResponseHelper queryHelper;

    @Inject
    public CryptoController(
            MetaAnswers metaAnswers,
            CryptoAnswers cryptoAnswers,
            TxnResponseHelper txnHelper,
            QueryResponseHelper queryHelper) {
        this.metaAnswers = metaAnswers;
        this.cryptoAnswers = cryptoAnswers;
        this.txnHelper = txnHelper;
        this.queryHelper = queryHelper;
    }

    @Override
    public void getAccountInfo(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(query, observer, cryptoAnswers.getAccountInfo(), CryptoGetInfo);
    }

    @Override
    public void cryptoGetBalance(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(
                query, observer, cryptoAnswers.getAccountBalance(), CryptoGetAccountBalance);
    }

    @Override
    public void getAccountRecords(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(
                query, observer, cryptoAnswers.getAccountRecords(), CryptoGetAccountRecords);
    }

    @Override
    public void getStakersByAccountID(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(query, observer, cryptoAnswers.getStakers(), CryptoGetStakers);
    }

    @Override
    public void getLiveHash(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(query, observer, cryptoAnswers.getLiveHash(), CryptoGetLiveHash);
    }

    @Override
    public void getTransactionReceipts(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(query, observer, metaAnswers.getTxnReceipt(), TransactionGetReceipt);
    }

    @Override
    public void getTxRecordByTxID(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(query, observer, metaAnswers.getTxnRecord(), TransactionGetRecord);
    }

    @Override
    public void getFastTransactionRecord(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(query, observer, metaAnswers.getFastTxnRecord(), NONE);
    }

    @Override
    public void cryptoTransfer(
            Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, CryptoTransfer);
    }

    @Override
    public void cryptoDelete(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, CryptoDelete);
    }

    @Override
    public void createAccount(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, CryptoCreate);
    }

    @Override
    public void updateAccount(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, CryptoUpdate);
    }

    @Override
    public void approveAllowances(
            Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, CryptoApproveAllowance);
    }

    @Override
    public void deleteAllowances(
            Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, CryptoDeleteAllowance);
    }

    @Override
    public void addLiveHash(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, CryptoAddLiveHash);
    }

    @Override
    public void deleteLiveHash(
            Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, CryptoDeleteLiveHash);
    }
}
