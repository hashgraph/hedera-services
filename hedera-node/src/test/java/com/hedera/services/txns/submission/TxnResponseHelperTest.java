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

import static com.hedera.services.txns.submission.TxnResponseHelper.FAIL_INVALID_RESPONSE;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.Mockito.mockStatic;

import com.hedera.services.stats.HapiOpCounters;
import com.hedera.services.txns.SubmissionFlow;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({LogCaptureExtension.class})
class TxnResponseHelperTest {
    private static final Transaction txn = Transaction.getDefaultInstance();
    private TransactionResponse okResponse;
    private TransactionResponse notOkResponse;

    private SubmissionFlow submissionFlow;
    private HapiOpCounters opCounters;
    private StreamObserver<TransactionResponse> observer;

    @LoggingTarget private LogCaptor logCaptor;

    @LoggingSubject private TxnResponseHelper subject;

    @BeforeEach
    void setup() {
        submissionFlow = mock(SubmissionFlow.class);
        opCounters = mock(HapiOpCounters.class);
        observer = mock(StreamObserver.class);
        okResponse = mock(TransactionResponse.class);
        given(okResponse.getNodeTransactionPrecheckCode()).willReturn(OK);
        notOkResponse = mock(TransactionResponse.class);

        subject = new TxnResponseHelper(submissionFlow, opCounters);
    }

    @Test
    void helpsWithSubmitHappyPath() {
        final var inOrder = inOrder(submissionFlow, opCounters, observer);
        given(submissionFlow.submit(txn)).willReturn(okResponse);

        subject.submit(txn, observer, CryptoTransfer);

        inOrder.verify(opCounters).countReceived(CryptoTransfer);
        inOrder.verify(submissionFlow).submit(txn);
        inOrder.verify(observer).onNext(okResponse);
        inOrder.verify(observer).onCompleted();
        inOrder.verify(opCounters).countSubmitted(CryptoTransfer);
    }

    @Test
    void helpsWithSubmitUnhappyPath() {
        final var inOrder = inOrder(submissionFlow, opCounters, observer);
        given(submissionFlow.submit(txn)).willReturn(notOkResponse);

        subject.submit(txn, observer, CryptoTransfer);

        inOrder.verify(opCounters).countReceived(CryptoTransfer);
        inOrder.verify(submissionFlow).submit(txn);
        inOrder.verify(observer).onNext(notOkResponse);
        inOrder.verify(observer).onCompleted();
        inOrder.verify(opCounters, never()).countSubmitted(CryptoTransfer);
    }

    @Test
    void helpsWithExceptionOnSubmit() {
        final var accessor = mock(SignedTxnAccessor.class);
        given(accessor.getSignedTxnWrapper()).willReturn(null);
        final var inOrder = inOrder(submissionFlow, opCounters, accessor, observer);

        try (final var accessorFactory = mockStatic(SignedTxnAccessor.class)) {
            accessorFactory.when(() -> SignedTxnAccessor.uncheckedFrom(txn)).thenReturn(accessor);
            given(submissionFlow.submit(txn)).willThrow(IllegalArgumentException.class);

            subject.submit(txn, observer, CryptoTransfer);

            inOrder.verify(opCounters).countReceived(CryptoTransfer);
            inOrder.verify(submissionFlow).submit(txn);
            inOrder.verify(accessor).getSignedTxnWrapper();
            assertThat(
                    logCaptor.warnLogs(),
                    contains(
                            "Submission flow unable to submit null!"
                                    + " java.lang.IllegalArgumentException: null"));
            inOrder.verify(observer).onNext(FAIL_INVALID_RESPONSE);
            inOrder.verify(observer).onCompleted();
            inOrder.verify(opCounters, never()).countSubmitted(CryptoTransfer);
        }
    }
}
