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
package com.hedera.services.stats;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NONE;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.system.Platform;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HapiOpCountersTest {
    private Platform platform;
    private Counter counter;
    private MiscRunningAvgs runningAvgs;
    private TransactionContext txnCtx;
    private Function<HederaFunctionality, String> statNameFn;
    private Metrics metrics;

    private HapiOpCounters subject;

    @BeforeEach
    void setup() {
        HapiOpCounters.setAllFunctions(
                () ->
                        new HederaFunctionality[] {
                            CryptoTransfer, TokenGetInfo, ConsensusSubmitMessage, NONE
                        });

        txnCtx = mock(TransactionContext.class);
        counter = mock(Counter.class);
        platform = mock(Platform.class);
        statNameFn = HederaFunctionality::toString;
        runningAvgs = mock(MiscRunningAvgs.class);
        metrics = mock(Metrics.class);

        subject = new HapiOpCounters(runningAvgs, txnCtx, statNameFn);

        given(platform.getMetrics()).willReturn(metrics);
        given(metrics.getOrCreate(any())).willReturn(counter);

        subject.registerWith(platform);
    }

    @AfterEach
    void cleanup() {
        HapiOpCounters.allFunctions = HederaFunctionality.class::getEnumConstants;
    }

    @Test
    void beginsRationally() {
        assertTrue(subject.getReceivedOps().containsKey(CryptoTransfer));
        assertTrue(subject.getSubmittedTxns().containsKey(CryptoTransfer));
        assertTrue(subject.getHandledTxns().containsKey(CryptoTransfer));
        assertEquals(0, subject.getDeprecatedTxns().get());
        assertFalse(subject.getAnsweredQueries().containsKey(CryptoTransfer));

        assertTrue(subject.getReceivedOps().containsKey(TokenGetInfo));
        assertTrue(subject.getAnsweredQueries().containsKey(TokenGetInfo));
        assertFalse(subject.getSubmittedTxns().containsKey(TokenGetInfo));
        assertFalse(subject.getHandledTxns().containsKey(TokenGetInfo));

        assertFalse(subject.getReceivedOps().containsKey(NONE));
        assertFalse(subject.getSubmittedTxns().containsKey(NONE));
        assertFalse(subject.getAnsweredQueries().containsKey(NONE));
        assertFalse(subject.getHandledTxns().containsKey(NONE));
    }

    @Test
    void registersExpectedStatEntries() {
        verify(metrics, times(9)).getOrCreate(any());
    }

    @Test
    void updatesAvgSubmitMessageHdlSizeForHandled() {
        final var expectedSize = 12345;
        final var txn = mock(TransactionBody.class);
        final var accessor = mock(SignedTxnAccessor.class);
        given(txn.getSerializedSize()).willReturn(expectedSize);
        given(accessor.getTxn()).willReturn(txn);
        given(txnCtx.accessor()).willReturn(accessor);

        subject.countHandled(ConsensusSubmitMessage);

        verify(runningAvgs).recordHandledSubmitMessageSize(expectedSize);
    }

    @Test
    void doesntUpdateAvgSubmitMessageHdlSizeForCountReceivedOrSubmitted() {
        final var expectedSize = 12345;
        final var txn = mock(TransactionBody.class);
        final var accessor = mock(PlatformTxnAccessor.class);
        given(txn.getSerializedSize()).willReturn(expectedSize);
        given(accessor.getTxn()).willReturn(txn);
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);

        subject.countReceived(ConsensusSubmitMessage);
        subject.countSubmitted(ConsensusSubmitMessage);

        verify(runningAvgs, never()).recordHandledSubmitMessageSize(expectedSize);
    }

    @Test
    void updatesExpectedEntries() {
        subject.countReceived(CryptoTransfer);
        subject.countReceived(CryptoTransfer);
        subject.countReceived(CryptoTransfer);
        subject.countSubmitted(CryptoTransfer);
        subject.countSubmitted(CryptoTransfer);
        subject.countHandled(CryptoTransfer);
        subject.countDeprecatedTxnReceived();
        subject.countReceived(TokenGetInfo);
        subject.countReceived(TokenGetInfo);
        subject.countReceived(TokenGetInfo);
        subject.countAnswered(TokenGetInfo);
        subject.countAnswered(TokenGetInfo);

        verify(counter, times(12)).increment();
    }

    @Test
    void ignoredOpsAreNoops() {
        assertDoesNotThrow(() -> subject.countReceived(NONE));
        assertDoesNotThrow(() -> subject.countSubmitted(NONE));
        assertDoesNotThrow(() -> subject.countHandled(NONE));
        assertDoesNotThrow(() -> subject.countAnswered(NONE));

        assertEquals(0L, subject.receivedSoFar(NONE));
        assertEquals(0L, subject.submittedSoFar(NONE));
        assertEquals(0L, subject.handledSoFar(NONE));
        assertEquals(0L, subject.answeredSoFar(NONE));
    }

    @Test
    void deprecatedTxnsCountIncrementsByOne() {
        subject.countDeprecatedTxnReceived();
        verify(counter).increment();
    }
}
