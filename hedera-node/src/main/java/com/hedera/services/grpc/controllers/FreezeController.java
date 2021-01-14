package com.hedera.services.grpc.controllers;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.FreezeServiceGrpc;
import io.grpc.stub.StreamObserver;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;

public class FreezeController extends FreezeServiceGrpc.FreezeServiceImplBase {
	public static final String FREEZE_METRIC = "freeze";

	private final TxnResponseHelper txnHelper;

	public FreezeController(TxnResponseHelper txnHelper) {
		this.txnHelper = txnHelper;
	}

	@Override
	public void freeze(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
		txnHelper.submit(signedTxn, observer, Freeze);
	}
}
