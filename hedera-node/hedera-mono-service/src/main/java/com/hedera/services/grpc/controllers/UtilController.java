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

import static com.hederahashgraph.api.proto.java.HederaFunctionality.UtilPrng;

import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.UtilServiceGrpc;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UtilController extends UtilServiceGrpc.UtilServiceImplBase {
    private final TxnResponseHelper txnHelper;

    @Inject
    public UtilController(TxnResponseHelper txnHelper) {
        this.txnHelper = txnHelper;
    }

    @Override
    public void prng(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, UtilPrng);
    }
}
