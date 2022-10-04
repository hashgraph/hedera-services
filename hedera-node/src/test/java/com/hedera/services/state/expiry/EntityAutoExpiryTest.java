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

import static com.hedera.services.state.tasks.SystemTaskResult.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.config.MockHederaNumbers;
import com.hedera.services.records.ConsensusTimeTracker;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.logic.NetworkCtxManager;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.state.tasks.SystemTaskManager;
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

    @Mock private SystemTaskManager taskManager;
    @Mock private SequenceNumber seqNo;
    @Mock private NetworkCtxManager networkCtxManager;
    @Mock private MerkleNetworkContext networkCtx;
    @Mock private ConsensusTimeTracker consensusTimeTracker;
    @Mock private ExpiryThrottle expiryThrottle;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private ExpiryStats expiryStats;

    private EntityAutoExpiry subject;

    @BeforeEach
    void setUp() {
        subject =
                new EntityAutoExpiry(
                        expiryStats,
                        mockHederaNums,
                        expiryThrottle,
                        recordsHistorian,
                        taskManager,
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
        verifyNoInteractions(taskManager);
        verify(networkCtx).syncExpiryThrottle(expiryThrottle);
    }

    @Test
    void abortsIfExpiryThrottleCannotSupportMinUnitOfWork() {
        given(consensusTimeTracker.hasMoreStandaloneRecordTime()).willReturn(true);
        given(expiryThrottle.stillLacksMinFreeCapAfterLeakingUntil(instantNow)).willReturn(true);

        // when:
        subject.execute(instantNow);

        // then:
        verifyNoInteractions(taskManager);
        verify(networkCtx).syncExpiryThrottle(expiryThrottle);
    }

    @Test
    void abortsIfNoMoreStandaloneRecordTime() {
        // setup:
        given(consensusTimeTracker.hasMoreStandaloneRecordTime()).willReturn(false);

        // when:
        subject.execute(instantNow);

        // then:
        verifyNoInteractions(taskManager);
    }

    @Test
    void abortsIfSystemTxnIdIsUnknown() {
        // setup:
        given(consensusTimeTracker.hasMoreStandaloneRecordTime()).willReturn(true);
        given(recordsHistorian.nextSystemTransactionIdIsUnknown()).willReturn(true);

        // when:
        subject.execute(instantNow);

        // then:
        verifyNoInteractions(taskManager);
    }

    @Test
    void abortsIfNoNonSystemEntities() {
        // setup:
        givenWrapNum(mockHederaNums.numReservedSystemEntities() + 1);

        // when:
        subject.execute(instantNow);

        // then:
        verifyNoInteractions(taskManager);
    }

    @Test
    void resetsSummaryCountsIfNewConsensusSecond() {
        given(consensusTimeTracker.hasMoreStandaloneRecordTime()).willReturn(true);
        given(networkCtxManager.currentTxnIsFirstInConsensusSecond()).willReturn(true);
        givenWrapNum(aNum + 123);
        givenLastScanned(aNum - 1);
        given(taskManager.process(anyLong(), any(), any())).willReturn(NOTHING_TO_DO);
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
        given(taskManager.process(anyLong(), eq(instantNow), eq(networkCtx)))
                .willReturn(NOTHING_TO_DO);

        // when:
        subject.execute(instantNow);

        // then:
        for (long i = aNum; i < aNum + numToScan; i++) {
            verify(taskManager).process(i, instantNow, networkCtx);
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
        given(taskManager.process(aNum, instantNow, networkCtx))
                .willReturn(NEEDS_DIFFERENT_CONTEXT);
        mockDynamicProps.setMaxToTouch(1);

        // when:
        subject.execute(instantNow);

        // then:
        verify(taskManager, times(1)).process(aNum, instantNow, networkCtx);
        verifyNoMoreInteractions(taskManager);
        verify(networkCtx).updateAutoRenewSummaryCounts(1, 0);
        verify(networkCtx).updateLastScannedEntity(aNum - 1);
    }

    @Test
    void stopsEarlyWhenLotsToTouch() {
        // setup:
        given(consensusTimeTracker.hasMoreStandaloneRecordTime()).willReturn(true);
        long numToScan = mockDynamicProps.autoRenewNumberOfEntitiesToScan();

        givenWrapNum(aNum + numToScan);
        givenLastScanned(aNum - 1);
        given(taskManager.process(aNum, instantNow, networkCtx)).willReturn(DONE);
        given(taskManager.process(bNum, instantNow, networkCtx)).willReturn(DONE);

        // when:
        subject.execute(instantNow);

        // then:
        for (long i = aNum; i < cNum; i++) {
            verify(taskManager).process(i, instantNow, networkCtx);
        }
        // and:
        verify(taskManager, never()).process(cNum, instantNow, networkCtx);
        verify(networkCtx).updateLastScannedEntity(bNum);
    }

    @Test
    void stopsEarlyWhenNoMoreStandaloneRecordTime() {
        // setup:
        given(consensusTimeTracker.hasMoreStandaloneRecordTime()).willReturn(true);
        long numToScan = mockDynamicProps.autoRenewNumberOfEntitiesToScan();

        givenWrapNum(aNum + numToScan);
        givenLastScanned(aNum - 1);
        given(taskManager.process(aNum, instantNow, networkCtx))
                .willAnswer(
                        i -> {
                            given(consensusTimeTracker.hasMoreStandaloneRecordTime())
                                    .willReturn(false);
                            return DONE;
                        });

        // when:
        subject.execute(instantNow);

        // then:
        verify(taskManager).process(aNum, instantNow, networkCtx);
        // and:
        verify(taskManager, never()).process(bNum, instantNow, networkCtx);
        verify(networkCtx).updateLastScannedEntity(aNum);
    }

    @Test
    void understandsHowToWrap() {
        // setup:
        given(consensusTimeTracker.hasMoreStandaloneRecordTime()).willReturn(true);
        long numToScan = mockDynamicProps.autoRenewNumberOfEntitiesToScan();

        givenWrapNum(aNum + numToScan);
        givenLastScanned(aNum + numToScan - 2);
        given(taskManager.process(aNum + numToScan - 1, instantNow, networkCtx))
                .willReturn(NOTHING_TO_DO);
        given(taskManager.process(aNum - 1, instantNow, networkCtx)).willReturn(NOTHING_TO_DO);
        given(taskManager.process(aNum, instantNow, networkCtx)).willReturn(DONE);
        given(taskManager.process(bNum, instantNow, networkCtx)).willReturn(DONE);

        // when:
        subject.execute(instantNow);

        // then:
        verify(taskManager).process(aNum + numToScan - 1, instantNow, networkCtx);
        for (long i = aNum; i < cNum; i++) {
            verify(taskManager).process(i, instantNow, networkCtx);
        }
        // and:
        verify(taskManager, never()).process(cNum, instantNow, networkCtx);
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
