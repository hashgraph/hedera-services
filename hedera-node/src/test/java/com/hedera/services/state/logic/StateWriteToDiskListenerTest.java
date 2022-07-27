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
package com.hedera.services.state.logic;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.txns.network.UpgradeActions;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteNotification;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class StateWriteToDiskListenerTest {
    private static final long round = 234L;
    private static final long sequence = 123L;
    private static final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);

    @Mock private StateWriteToDiskCompleteNotification notification;
    @Mock private UpgradeActions upgradeActions;

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private StateWriteToDiskListener subject;

    @BeforeEach
    void setUp() {
        subject = new StateWriteToDiskListener(upgradeActions);
    }

    @Test
    void notifiesWhenFrozen() {
        given(notification.getSequence()).willReturn(sequence);
        given(notification.getRoundNumber()).willReturn(round);
        given(notification.getConsensusTimestamp()).willReturn(consensusNow);
        given(notification.isFreezeState()).willReturn(true);

        subject.notify(notification);

        assertThat(
                logCaptor.infoLogs(),
                contains(
                        "Notification Received: Freeze State Finished. consensusTimestamp:"
                            + " 1970-01-15T06:56:07.000000890Z, roundNumber: 234, sequence: 123"));
        verify(upgradeActions).externalizeFreezeIfUpgradePending();
    }

    @Test
    void doesntNotifyForEverySignedStateWritten() {
        given(notification.isFreezeState()).willReturn(false);

        subject.notify(notification);

        verify(upgradeActions, never()).externalizeFreezeIfUpgradePending();
    }
}
