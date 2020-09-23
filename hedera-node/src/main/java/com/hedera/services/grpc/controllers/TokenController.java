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
import com.hedera.services.queries.token.TokenAnswers;
import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.TokenServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenController extends TokenServiceGrpc.TokenServiceImplBase {
	private static final Logger log = LogManager.getLogger(TokenController.class);

	public static final String TOKEN_MINT_METRIC = "mintToken";
	public static final String TOKEN_BURN_METRIC = "burnToken";
	public static final String TOKEN_CREATE_METRIC = "createToken";
	public static final String TOKEN_DELETE_METRIC = "deleteToken";
	public static final String TOKEN_UPDATE_METRIC = "updateToken";
	public static final String TOKEN_TRANSACT_METRIC = "transferTokens";
	public static final String TOKEN_FREEZE_METRIC = "freezeTokenAccount";
	public static final String TOKEN_UNFREEZE_METRIC = "unfreezeTokenAccount";
	public static final String TOKEN_GRANT_KYC_METRIC = "grantKycToTokenAccount";
	public static final String TOKEN_REVOKE_KYC_METRIC = "revokeKycFromTokenAccount";
	public static final String TOKEN_WIPE_ACCOUNT_METRIC = "wipeTokenAccount";
	public static final String TOKEN_ASSOCIATE_METRIC = "associateTokens";
	public static final String TOKEN_DISSOCIATE_METRIC = "dissociateTokens";

	public static final String TOKEN_GET_INFO_METRIC = "getTokenInfo";

	private final TokenAnswers tokenAnswers;
	private final TxnResponseHelper txnHelper;
	private final QueryResponseHelper queryHelper;

	public TokenController(
			TokenAnswers tokenAnswers,
			TxnResponseHelper txnHelper,
			QueryResponseHelper queryHelper
	) {
		this.txnHelper = txnHelper;
		this.queryHelper = queryHelper;
		this.tokenAnswers = tokenAnswers;
	}

	@Override
	public void createToken(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToToken(signedTxn, observer, TOKEN_CREATE_METRIC);
	}

	@Override
	public void deleteToken(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToToken(signedTxn, observer, TOKEN_DELETE_METRIC);
	}

	@Override
	public void mintToken(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToToken(signedTxn, observer, TOKEN_MINT_METRIC);
	}

	@Override
	public void burnToken(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToToken(signedTxn, observer, TOKEN_BURN_METRIC);
	}

	@Override
	public void updateToken(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToToken(signedTxn, observer, TOKEN_UPDATE_METRIC);
	}

	@Override
	public void wipeTokenAccount(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToToken(signedTxn, observer, TOKEN_WIPE_ACCOUNT_METRIC);
	}

	@Override
	public void freezeTokenAccount(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToToken(signedTxn, observer, TOKEN_FREEZE_METRIC);
	}

	@Override
	public void unfreezeTokenAccount(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToToken(signedTxn, observer, TOKEN_UNFREEZE_METRIC);
	}

	@Override
	public void grantKycToTokenAccount(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToToken(signedTxn, observer, TOKEN_GRANT_KYC_METRIC);
	}

	@Override
	public void revokeKycFromTokenAccount(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToToken(signedTxn, observer, TOKEN_REVOKE_KYC_METRIC);
	}

	@Override
	public void transferTokens(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToToken(signedTxn, observer, TOKEN_TRANSACT_METRIC);
	}

	@Override
	public void associateTokens(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToToken(signedTxn, observer, TOKEN_ASSOCIATE_METRIC);
	}

	@Override
	public void dissociateTokens(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToToken(signedTxn, observer, TOKEN_DISSOCIATE_METRIC);
	}

	@Override
	public void getTokenInfo(Query query, StreamObserver<Response> observer) {
		queryHelper.respondToToken(query, observer, tokenAnswers.getTokenInfo(), TOKEN_GET_INFO_METRIC);
	}
}
