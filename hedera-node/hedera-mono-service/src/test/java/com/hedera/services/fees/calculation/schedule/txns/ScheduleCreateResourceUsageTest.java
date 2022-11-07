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
package com.hedera.services.fees.calculation.schedule.txns;

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.schedule.ScheduleOpsUsage;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.services.usage.schedule.ScheduleOpsUsage.ONE_MONTH_IN_SECS;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ScheduleCreateResourceUsageTest {
    ScheduleCreateResourceUsage subject;
    StateView view;
    ScheduleOpsUsage scheduleOpsUsage;
    TransactionBody nonScheduleCreateTxn;
    TransactionBody scheduleCreateTxn;

    final int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
    final SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
    final SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);
    final MockGlobalDynamicProps props = new MockGlobalDynamicProps();
    FeeData expected;

    @BeforeEach
    void setup() {
        expected = mock(FeeData.class);
        view = mock(StateView.class);
        scheduleOpsUsage = mock(ScheduleOpsUsage.class);
        nonScheduleCreateTxn = mock(TransactionBody.class);

        subject = new ScheduleCreateResourceUsage(scheduleOpsUsage, props);
    }

    @Test
    void recognizesApplicableQuery() {
        scheduleCreateTxn =  givenScheduleCreate(2L, 5L);

        assertTrue(subject.applicableTo(scheduleCreateTxn));
        assertFalse(subject.applicableTo(nonScheduleCreateTxn));
    }

    @Test
    void delegatesToCorrectEstimate() throws Exception {
        scheduleCreateTxn =  givenScheduleCreate(2L, 5L);
        given(
                        scheduleOpsUsage.scheduleCreateUsage(
                                scheduleCreateTxn,
                                sigUsage,
                                props.scheduledTxExpiryTimeSecs(),
                                props.scheduledTxExpiryTimeSecs()))
                .willReturn(expected);
        // expect:
        assertEquals(expected, subject.usageGiven(scheduleCreateTxn, obj, view));
    }

    @Test
    void handlesExpirationTime() throws Exception {
        props.enableSchedulingLongTerm();
        scheduleCreateTxn =  givenScheduleCreate(2L, 1L);

        given(scheduleOpsUsage.scheduleCreateUsage(scheduleCreateTxn, sigUsage,
                1L, props.scheduledTxExpiryTimeSecs())).willReturn(expected);

        assertEquals(expected, subject.usageGiven(scheduleCreateTxn, obj, view));
    }

    @Test
    void ignoresExpirationTimeLongTermDisabled() throws Exception {
        final var txn = givenScheduleCreate(2L, 5L);

        given(
                scheduleOpsUsage.scheduleCreateUsage(
                        txn,
                        sigUsage,
                        props.scheduledTxExpiryTimeSecs(),
                        props.scheduledTxExpiryTimeSecs()))
                .willReturn(expected);

        assertEquals(expected, subject.usageGiven(txn, obj, view));
    }

    @Test
    void handlesPastExpirationTime() throws Exception {
        props.enableSchedulingLongTerm();
        scheduleCreateTxn = givenScheduleCreate(1L, 5L);

        given(scheduleOpsUsage.scheduleCreateUsage(scheduleCreateTxn, sigUsage, 0L, 1800))
                .willReturn(expected);

        assertEquals(expected, subject.usageGiven(scheduleCreateTxn, obj, view));
    }

    @Test
    void returnsHasSecondaryFee(){
        assertTrue(subject.hasSecondaryFees());
    }

    @Test
    void calculatesSecondaryFeesForLongExpiryTxns() throws InvalidTxBodyException {
        props.enableSchedulingLongTerm();
        final var txn = givenScheduleCreate(ONE_MONTH_IN_SECS, 5L);

        subject.usageGiven(txn, obj, view);
        final var feeObject = subject.secondaryFeesFor(txn);

        assertEquals(0L, feeObject.getNetworkFee());
        assertEquals(0L, feeObject.getNodeFee());
        assertEquals(5156240L, feeObject.getServiceFee());
    }

    @Test
    void calculatesSecondaryFeesForDefaultExpiryTxns() throws InvalidTxBodyException {
        props.enableSchedulingLongTerm();
        final var txn = givenScheduleCreate(1800L, 5L);

        subject.usageGiven(txn, obj, view);
        final var feeObject = subject.secondaryFeesFor(txn);

        assertEquals(0L, feeObject.getNetworkFee());
        assertEquals(0L, feeObject.getNodeFee());
        assertEquals(0L, feeObject.getServiceFee());
    }

    @Test
    void calculatesSecondaryFeesForLessThanDefaultExpiryTxns() throws InvalidTxBodyException {
        props.enableSchedulingLongTerm();
        final var txn = givenScheduleCreate(60L, 5L);

        subject.usageGiven(txn, obj, view);
        final var feeObject = subject.secondaryFeesFor(txn);

        assertEquals(0L, feeObject.getNetworkFee());
        assertEquals(0L, feeObject.getNodeFee());
        assertEquals(0L, feeObject.getServiceFee());
    }

    TransactionBody givenScheduleCreate(final long expirySecs, final long transactionValidStartSecs){
        final var transactionID =
                TransactionID.newBuilder()
                        .setTransactionValidStart(
                                Timestamp.newBuilder().setSeconds(transactionValidStartSecs).setNanos(0))
                        .build();
        final var body =
                ScheduleCreateTransactionBody.newBuilder()
                        .setExpirationTime(
                                Timestamp.newBuilder().setSeconds(expirySecs).setNanos(0))
                        .setScheduledTransactionBody( SchedulableTransactionBody.newBuilder()
                                .setCryptoTransfer(
                                        CryptoTransferTransactionBody
                                                .newBuilder()
                                                .setTransfers(
                                                        TransferList
                                                                .newBuilder()
                                                                .addAccountAmounts(
                                                                        AccountAmount
                                                                                .newBuilder()
                                                                                .setAmount(
                                                                                        -1_000_000_000)
                                                                                .setAccountID(
                                                                                        asAccount("0.0.10")))
                                                                .addAccountAmounts(
                                                                        AccountAmount
                                                                                .newBuilder()
                                                                                .setAmount(
                                                                                        +1_000_000_000)
                                                                                .setAccountID(
                                                                                        asAccount("0.0.100")))))
                                .setMemo("")
                                .setTransactionFee(100_000_000L)
                                .build())
                        .build();
        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setScheduleCreate(body)
                .build();
    }
}
