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

import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetAccountDetails;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetVersionInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NetworkGetExecutionTime;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UncheckedSubmit;

import com.hedera.services.queries.answering.QueryResponseHelper;
import com.hedera.services.queries.meta.MetaAnswers;
import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.NetworkServiceGrpc;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NetworkController extends NetworkServiceGrpc.NetworkServiceImplBase {
    private final MetaAnswers metaAnswers;
    private final TxnResponseHelper txnResponseHelper;
    private final QueryResponseHelper queryHelper;

    public static final String GET_VERSION_INFO_METRIC = "getVersionInfo";
    public static final String GET_EXECUTION_TIME_METRIC = "getExecutionTime";
    public static final String UNCHECKED_SUBMIT_METRIC = "uncheckedSubmit";
    public static final String GET_ACCOUNT_DETAILS_METRIC = "getAccountDetails";

    @Inject
    public NetworkController(
            final MetaAnswers metaAnswers,
            final TxnResponseHelper txnResponseHelper,
            final QueryResponseHelper queryHelper) {
        this.metaAnswers = metaAnswers;
        this.txnResponseHelper = txnResponseHelper;
        this.queryHelper = queryHelper;
    }

    @Override
    public void getVersionInfo(final Query query, final StreamObserver<Response> observer) {
        queryHelper.answer(query, observer, metaAnswers.getVersionInfo(), GetVersionInfo);
    }

    @Override
    public void getExecutionTime(final Query query, final StreamObserver<Response> observer) {
        queryHelper.answer(query, observer, metaAnswers.getExecTime(), NetworkGetExecutionTime);
    }

    @Override
    public void uncheckedSubmit(
            final Transaction signedTxn, final StreamObserver<TransactionResponse> observer) {
        txnResponseHelper.submit(signedTxn, observer, UncheckedSubmit);
    }

    @Override
    public void getAccountDetails(final Query query, final StreamObserver<Response> observer) {
        queryHelper.answer(query, observer, metaAnswers.getAccountDetails(), GetAccountDetails);
    }
}
