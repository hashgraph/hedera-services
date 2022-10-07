/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BasicSubmissionFlowTest {
    private static final long someReqFee = 1_234L;
    private static final TxnValidityAndFeeReq someFailure =
            new TxnValidityAndFeeReq(INSUFFICIENT_TX_FEE, someReqFee);
    private static final TxnValidityAndFeeReq someSuccess =
            new TxnValidityAndFeeReq(OK, someReqFee);

    private static final Transaction someTxn = Transaction.getDefaultInstance();
    private static final SignedTxnAccessor someAccessor = SignedTxnAccessor.uncheckedFrom(someTxn);

    @Mock private NodeInfo nodeInfo;
    @Mock private TransactionPrecheck precheck;
    @Mock private PlatformSubmissionManager submissionManager;

    private BasicSubmissionFlow subject;

    @Test
    void rejectsAllOnZeroStakeNode() {
        setupZeroStakeNode();

        final var response = subject.submit(someTxn);

        assertEquals(INVALID_NODE_ACCOUNT, response.getNodeTransactionPrecheckCode());
    }

    @Test
    void rejectsPrecheckFailures() {
        setupStakedNode();
        given(precheck.performForTopLevel(someTxn)).willReturn(Pair.of(someFailure, null));

        final var response = subject.submit(someTxn);

        assertEquals(INSUFFICIENT_TX_FEE, response.getNodeTransactionPrecheckCode());
        assertEquals(someReqFee, response.getCost());
    }

    @Test
    void translatesPlatformCreateFailure() {
        setupStakedNode();
        givenValidPrecheck();
        given(submissionManager.trySubmission(any())).willReturn(PLATFORM_TRANSACTION_NOT_CREATED);

        final var response = subject.submit(someTxn);

        assertEquals(PLATFORM_TRANSACTION_NOT_CREATED, response.getNodeTransactionPrecheckCode());
    }

    @Test
    void rejectsInvalidAccessor() {
        setupStakedNode();
        given(precheck.performForTopLevel(someTxn)).willReturn(Pair.of(someSuccess, null));

        final var response = subject.submit(someTxn);

        assertEquals(FAIL_INVALID, response.getNodeTransactionPrecheckCode());
        verify(submissionManager, never()).trySubmission(any());
    }

    @Test
    void followsHappyPathToOk() {
        setupStakedNode();
        givenValidPrecheck();
        givenOkSubmission();

        final var response = subject.submit(someTxn);

        assertEquals(OK, response.getNodeTransactionPrecheckCode());
    }

    private void givenOkSubmission() {
        given(submissionManager.trySubmission(any())).willReturn(OK);
    }

    private void givenValidPrecheck() {
        given(precheck.performForTopLevel(someTxn)).willReturn(Pair.of(someSuccess, someAccessor));
    }

    private void setupStakedNode() {
        subject = new BasicSubmissionFlow(nodeInfo, precheck, submissionManager);
    }

    private void setupZeroStakeNode() {
        given(nodeInfo.isSelfZeroStake()).willReturn(true);
        subject = new BasicSubmissionFlow(nodeInfo, precheck, submissionManager);
    }
}
