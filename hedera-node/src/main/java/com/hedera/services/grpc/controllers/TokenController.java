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
import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.TokenServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenController extends TokenServiceGrpc.TokenServiceImplBase {
	private static final Logger log = LogManager.getLogger(TokenController.class);

	public static final String TOKEN_CREATE_METRIC = "createToken";

	private final TxnResponseHelper txnHelper;
	private final QueryResponseHelper queryHelper;

	public TokenController(
			TxnResponseHelper txnHelper,
			QueryResponseHelper queryHelper
	) {
		this.txnHelper = txnHelper;
		this.queryHelper = queryHelper;
	}

	@Override
	public void createToken(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.respondToToken(signedTxn, observer, TOKEN_CREATE_METRIC);
	}
}
