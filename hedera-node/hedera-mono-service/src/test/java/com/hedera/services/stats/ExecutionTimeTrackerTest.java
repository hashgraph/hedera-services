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
package com.hedera.services.stats;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutionTimeTrackerTest {
    private final TransactionID aTxnId =
            TransactionID.newBuilder()
                    .setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234_567L))
                    .setAccountID(IdUtils.asAccount("0.0.2"))
                    .build();
    private final TransactionID bTxnId =
            TransactionID.newBuilder()
                    .setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234_567L))
                    .setAccountID(IdUtils.asAccount("0.0.3"))
                    .build();

    @Mock private NodeLocalProperties nodeLocalProperties;
    @Mock private TransactionContext txnCtx;
    @Mock private SignedTxnAccessor accessor;

    private ExecutionTimeTracker subject;

    @Test
    void isNoopIfNotTrackingAnyTimes() {
        withImpliedSubject();

        assertTrue(subject.isShouldNoop());
        assertNull(subject.getExecNanosCache());
        assertDoesNotThrow(subject::stop);
        assertDoesNotThrow(subject::start);
        assertNull(subject.getExecNanosIfPresentFor(aTxnId));
    }

    @Test
    void tracksAtMostConfigured() {
        final var busyNanos = 5_000_000;

        given(nodeLocalProperties.numExecutionTimesToTrack()).willReturn(1);
        withImpliedSubject();
        given(txnCtx.accessor()).willReturn(accessor);

        given(accessor.getTxnId()).willReturn(aTxnId);

        subject.start();
        stayBusyFor(busyNanos);
        subject.stop();

        given(accessor.getTxnId()).willReturn(bTxnId);
        subject.start();
        stayBusyFor(busyNanos);
        subject.stop();

        assertNull(subject.getExecNanosIfPresentFor(aTxnId));
        assertNotNull(subject.getExecNanosIfPresentFor(bTxnId));
    }

    private void stayBusyFor(long nanos) {
        long now = System.nanoTime();
        while (System.nanoTime() - now < nanos) {
            /* No-op */
        }
    }

    private void withImpliedSubject() {
        subject = new ExecutionTimeTracker(txnCtx, nodeLocalProperties);
    }
}
