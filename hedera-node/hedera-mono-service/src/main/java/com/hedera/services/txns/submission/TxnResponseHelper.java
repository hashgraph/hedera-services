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
package com.hedera.services.txns.submission;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.stats.HapiOpCounters;
import com.hedera.services.txns.SubmissionFlow;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public final class TxnResponseHelper {
    private static final Logger log = LogManager.getLogger(TxnResponseHelper.class);

    static final TransactionResponse FAIL_INVALID_RESPONSE =
            TransactionResponse.newBuilder().setNodeTransactionPrecheckCode(FAIL_INVALID).build();

    private final SubmissionFlow submissionFlow;
    private final HapiOpCounters opCounters;

    @Inject
    public TxnResponseHelper(final SubmissionFlow submissionFlow, final HapiOpCounters opCounters) {
        this.opCounters = opCounters;
        this.submissionFlow = submissionFlow;
    }

    public void submit(
            final Transaction signedTxn,
            final StreamObserver<TransactionResponse> observer,
            final HederaFunctionality statedFunction) {
        respondWithMetrics(
                signedTxn,
                observer,
                () -> opCounters.countReceived(statedFunction),
                () -> opCounters.countSubmitted(statedFunction));
    }

    private void respondWithMetrics(
            final Transaction signedTxn,
            final StreamObserver<TransactionResponse> observer,
            final Runnable incReceivedCount,
            final Runnable incSubmittedCount) {
        incReceivedCount.run();
        TransactionResponse response;

        try {
            response = submissionFlow.submit(signedTxn);
        } catch (final Exception surprising) {
            final var accessor = SignedTxnAccessor.uncheckedFrom(signedTxn);
            log.warn(
                    "Submission flow unable to submit {}!",
                    accessor.getSignedTxnWrapper(),
                    surprising);
            response = FAIL_INVALID_RESPONSE;
        }

        observer.onNext(response);
        observer.onCompleted();

        if (response.getNodeTransactionPrecheckCode() == OK) {
            incSubmittedCount.run();
        }
    }
}
