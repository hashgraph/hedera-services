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
import com.hedera.services.queries.schedule.ScheduleAnswers;
import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ScheduleServiceGrpc;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleSign;

public class ScheduleController extends ScheduleServiceGrpc.ScheduleServiceImplBase {
    private static final Logger log = LogManager.getLogger(ScheduleController.class);

    private final ScheduleAnswers scheduleAnswers;
    private final TxnResponseHelper txnHelper;
    private final QueryResponseHelper queryHelper;

    public ScheduleController(
            ScheduleAnswers scheduleAnswers,
            TxnResponseHelper txnHelper,
            QueryResponseHelper queryHelper
    ) {
        this.txnHelper = txnHelper;
        this.queryHelper = queryHelper;
        this.scheduleAnswers = scheduleAnswers;
    }

    @Override
    public void createSchedule(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, ScheduleCreate);
    }

    @Override
    public void signSchedule(Transaction request, StreamObserver<TransactionResponse> responseObserver) {
        txnHelper.submit(request, responseObserver, ScheduleSign);
    }

    @Override
    public void deleteSchedule(Transaction request, StreamObserver<TransactionResponse> responseObserver) {
        txnHelper.submit(request, responseObserver, ScheduleDelete);
    }

    @Override
    public void getScheduleInfo(Query request, StreamObserver<Response> responseObserver) {
        queryHelper.answer(request, responseObserver, scheduleAnswers.getScheduleInfo(), ScheduleGetInfo);
    }
}
