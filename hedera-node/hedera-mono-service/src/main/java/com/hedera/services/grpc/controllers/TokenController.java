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

import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetAccountNftInfos;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetNftInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetNftInfos;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGrantKycToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenPause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenRevokeKycFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnpause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdate;

import com.hedera.services.queries.answering.QueryResponseHelper;
import com.hedera.services.queries.token.TokenAnswers;
import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.TokenServiceGrpc;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TokenController extends TokenServiceGrpc.TokenServiceImplBase {
    private final TokenAnswers tokenAnswers;
    private final TxnResponseHelper txnHelper;
    private final QueryResponseHelper queryHelper;

    @Inject
    public TokenController(
            TokenAnswers tokenAnswers,
            TxnResponseHelper txnHelper,
            QueryResponseHelper queryHelper) {
        this.txnHelper = txnHelper;
        this.queryHelper = queryHelper;
        this.tokenAnswers = tokenAnswers;
    }

    @Override
    public void createToken(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, TokenCreate);
    }

    @Override
    public void deleteToken(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, TokenDelete);
    }

    @Override
    public void mintToken(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, TokenMint);
    }

    @Override
    public void burnToken(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, TokenBurn);
    }

    @Override
    public void updateToken(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, TokenUpdate);
    }

    @Override
    public void wipeTokenAccount(
            Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, TokenAccountWipe);
    }

    @Override
    public void freezeTokenAccount(
            Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, TokenFreezeAccount);
    }

    @Override
    public void unfreezeTokenAccount(
            Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, TokenUnfreezeAccount);
    }

    @Override
    public void pauseToken(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, TokenPause);
    }

    @Override
    public void unpauseToken(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, TokenUnpause);
    }

    @Override
    public void grantKycToTokenAccount(
            Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, TokenGrantKycToAccount);
    }

    @Override
    public void revokeKycFromTokenAccount(
            Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, TokenRevokeKycFromAccount);
    }

    @Override
    public void associateTokens(
            Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, TokenAssociateToAccount);
    }

    @Override
    public void dissociateTokens(
            Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, TokenDissociateFromAccount);
    }

    @Override
    public void getTokenInfo(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(query, observer, tokenAnswers.getTokenInfo(), TokenGetInfo);
    }

    @Override
    public void getTokenNftInfo(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(query, observer, tokenAnswers.getNftInfoAnswer(), TokenGetNftInfo);
    }

    @Override
    public void getAccountNftInfos(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(
                query, observer, tokenAnswers.getAccountNftInfosAnswer(), TokenGetAccountNftInfos);
    }

    @Override
    public void updateTokenFeeSchedule(
            Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, TokenFeeScheduleUpdate);
    }

    @Override
    public void getTokenNftInfos(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(
                query, observer, tokenAnswers.getTokenNftInfosAnswer(), TokenGetNftInfos);
    }
}
