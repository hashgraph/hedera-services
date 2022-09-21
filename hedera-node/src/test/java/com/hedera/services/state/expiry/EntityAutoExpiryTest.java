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
package com.hedera.services.state.expiry;

import static com.hedera.services.state.expiry.ExpiryProcessResult.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.config.MockHederaNumbers;
import com.hedera.services.records.ConsensusTimeTracker;
import com.hedera.services.state.logic.NetworkCtxManager;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.stats.ExpiryStats;
import com.hedera.services.throttling.ExpiryThrottle;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityAutoExpiryTest {
    private final Instant instantNow = Instant.ofEpochSecond(1_234_567L);
    private final HederaNumbers mockHederaNums = new MockHederaNumbers();
    private final MockGlobalDynamicProps mockDynamicProps = new MockGlobalDynamicProps();

    private final long aNum = 1002L, bNum = 1003L, cNum = 1004L;

    @Mock private SequenceNumber seqNo;
    @Mock private ExpiryProcess expiryProcess;
    @Mock private NetworkCtxManager networkCtxManager;
    @Mock private MerkleNetworkContext networkCtx;
    @Mock private ConsensusTimeTracker consensusTimeTracker;
    @Mock private ExpiryThrottle expiryThrottle;
    @Mock private ExpiryStats expiryStats;

    private EntityAutoExpiry subject;

    @BeforeEach
    void setUp() {
        subject =
                new EntityAutoExpiry(
                        expiryStats,
                        mockHederaNums,
                        expiryThrottle,
                        expiryProcess,
                        mockDynamicProps,
                        networkCtxManager,
                        () -> networkCtx,
                        consensusTimeTracker,
                        () -> seqNo);
    }

    @Test
    void abortsIfNotAutoRenewing() {
        // setup:
        mockDynamicProps.disableAutoRenew();

        // when:
        subject.execute(instantNow);

        // then:
        verifyNoInteractions(expiryProcess);
        verify(networkCtx).syncExpiryThrottle(expiryThrottle);
    }

    @Test
    void abortsIfExpiryThrottleCannotSupportMinUnitOfWork() {
        given(consensusTimeTracker.hasMoreStandaloneRecordTime()).willReturn(true);
        given(expiryThrottle.stillLacksMinFreeCapAfterLeakingUntil(instantNow)).willReturn(true);

        // when:
        subject.execute(instantNow);

        // then:
        verifyNoInteractions(expiryProcess);
        verify(networkCtx).syncExpiryThrottle(expiryThrottle);
    }

    @Test
    void abortsIfNoMoreStandaloneRecordTime() {
        // setup:
        given(consensusTimeTracker.hasMoreStandaloneRecordTime()).willReturn(false);

        // when:
        subject.execute(instantNow);

        // then:
        verifyNoInteractions(expiryProcess);
    }

    @Test
    void abortsIfNoNonSystemEntities() {
        // setup:
        givenWrapNum(mockHederaNums.numReservedSystemEntities() + 1);

        // when:
        subject.execute(instantNow);

        // then:
        verifyNoInteractions(expiryProcess);
    }

    @Test
    void resetsSummaryCountsIfNewConsensusSecond() {
        given(consensusTimeTracker.hasMoreStandaloneRecordTime()).willReturn(true);
        given(networkCtxManager.currentTxnIsFirstInConsensusSecond()).willReturn(true);
        givenWrapNum(aNum + 123);
        givenLastScanned(aNum - 1);
        given(expiryProcess.process(anyLong(), any())).willReturn(NOTHING_TO_DO);
        given(networkCtx.idsScannedThisSecond()).willReturn(666L);

        // when:
        subject.execute(instantNow);

        // then:
        verify(expiryStats).includeIdsScannedInLastConsSec(666L);
        verify(networkCtx).clearAutoRenewSummaryCounts();
    }

    @Test
    void scansToExpectedNumWithNothingToTouch() {
        // setup:
        given(consensusTimeTracker.hasMoreStandaloneRecordTime()).willReturn(true);
        long numToScan = mockDynamicProps.autoRenewNumberOfEntitiesToScan();

        givenWrapNum(aNum + numToScan);
        givenLastScanned(aNum - 1);
        given(expiryProcess.process(anyLong(), eq(instantNow))).willReturn(NOTHING_TO_DO);

        // when:
        subject.execute(instantNow);

        // then:
        for (long i = aNum; i < aNum + numToScan; i++) {
            verify(expiryProcess).process(i, instantNow);
        }
        // and:
        verify(networkCtx).updateLastScannedEntity(aNum + numToScan - 1);
    }

    @Test
    void onlyAdvancesScanIfTouchedEntityIsDone() {
        final var numToScan = 3L;

        given(consensusTimeTracker.hasMoreStandaloneRecordTime()).willReturn(true);
        givenWrapNum(aNum + numToScan + 1);
        givenLastScanned(aNum - 1);
        given(expiryProcess.process(aNum, instantNow))
                .willReturn(STILL_MORE_TO_DO)
                .willReturn(DONE);
        mockDynamicProps.setMaxToTouch(1);

        // when:
        subject.execute(instantNow);

        // then:
        verify(expiryProcess, times(2)).process(aNum, instantNow);
        verifyNoMoreInteractions(expiryProcess);
        verify(networkCtx).updateAutoRenewSummaryCounts(1, 1);
        verify(networkCtx).updateLastScannedEntity(aNum);
    }

    @Test
    void lastEntityScannedDoesntChangeIfTouchedEntityIsntDone() {
        final var numToScan = 3L;

        given(consensusTimeTracker.hasMoreStandaloneRecordTime()).willReturn(true);
        givenWrapNum(aNum + numToScan + 1);
        givenLastScanned(aNum - 1);
        given(expiryProcess.process(aNum, instantNow))
                .willReturn(STILL_MORE_TO_DO)
                .willReturn(STILL_MORE_TO_DO)
                .willReturn(STILL_MORE_TO_DO)
                .willReturn(NO_CAPACITY_LEFT);

        // when:
        subject.execute(instantNow);

        // then:
        verify(expiryProcess, times(4)).process(aNum, instantNow);
        verifyNoMoreInteractions(expiryProcess);
        verify(networkCtx).updateLastScannedEntity(aNum - 1);
    }

    @Test
    void stopsEarlyWhenLotsToTouch() {
        // setup:
        given(consensusTimeTracker.hasMoreStandaloneRecordTime()).willReturn(true);
        long numToScan = mockDynamicProps.autoRenewNumberOfEntitiesToScan();

        givenWrapNum(aNum + numToScan);
        givenLastScanned(aNum - 1);
        given(expiryProcess.process(aNum, instantNow)).willReturn(DONE);
        given(expiryProcess.process(bNum, instantNow)).willReturn(DONE);

        // when:
        subject.execute(instantNow);

        // then:
        for (long i = aNum; i < cNum; i++) {
            verify(expiryProcess).process(i, instantNow);
        }
        // and:
        verify(expiryProcess, never()).process(cNum, instantNow);
        verify(networkCtx).updateLastScannedEntity(bNum);
    }

    @Test
    void stopsEarlyWhenNoMoreStandaloneRecordTime() {
        // setup:
        given(consensusTimeTracker.hasMoreStandaloneRecordTime()).willReturn(true);
        long numToScan = mockDynamicProps.autoRenewNumberOfEntitiesToScan();

        givenWrapNum(aNum + numToScan);
        givenLastScanned(aNum - 1);
        given(expiryProcess.process(aNum, instantNow))
                .willAnswer(
                        i -> {
                            given(consensusTimeTracker.hasMoreStandaloneRecordTime())
                                    .willReturn(false);
                            return DONE;
                        });

        // when:
        subject.execute(instantNow);

        // then:
        verify(expiryProcess).process(aNum, instantNow);
        // and:
        verify(expiryProcess, never()).process(bNum, instantNow);
        verify(networkCtx).updateLastScannedEntity(aNum);
    }

    @Test
    void understandsHowToWrap() {
        // setup:
        given(consensusTimeTracker.hasMoreStandaloneRecordTime()).willReturn(true);
        long numToScan = mockDynamicProps.autoRenewNumberOfEntitiesToScan();

        givenWrapNum(aNum + numToScan);
        givenLastScanned(aNum + numToScan - 2);
        given(expiryProcess.process(aNum + numToScan - 1, instantNow)).willReturn(NOTHING_TO_DO);
        given(expiryProcess.process(aNum - 1, instantNow)).willReturn(NOTHING_TO_DO);
        given(expiryProcess.process(aNum, instantNow)).willReturn(DONE);
        given(expiryProcess.process(bNum, instantNow)).willReturn(DONE);

        // when:
        subject.execute(instantNow);

        // then:
        verify(expiryProcess).process(aNum + numToScan - 1, instantNow);
        for (long i = aNum; i < cNum; i++) {
            verify(expiryProcess).process(i, instantNow);
        }
        // and:
        verify(expiryProcess, never()).process(cNum, instantNow);
        verify(networkCtx).updateLastScannedEntity(bNum);
        // and:
        verify(networkCtx).updateAutoRenewSummaryCounts(4, 2);
    }

    private void givenWrapNum(long num) {
        given(seqNo.current()).willReturn(num);
    }

    private void givenLastScanned(long num) {
        given(networkCtx.lastScannedEntity()).willReturn(num);
    }
}
