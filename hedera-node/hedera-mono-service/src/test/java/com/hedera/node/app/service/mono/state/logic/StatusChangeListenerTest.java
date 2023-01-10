/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.state.logic;

import static com.swirlds.common.system.PlatformStatus.ACTIVE;
import static com.swirlds.common.system.PlatformStatus.FREEZE_COMPLETE;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.context.CurrentPlatformStatus;
import com.hedera.node.app.service.mono.stream.RecordStreamManager;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.swirlds.common.notification.listeners.PlatformStatusChangeNotification;
import com.swirlds.common.system.NodeId;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class StatusChangeListenerTest {
    private static final long round = 234L;
    private static final long sequence = 123L;
    private static final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);

    @Mock private PlatformStatusChangeNotification notification;
    @Mock private CurrentPlatformStatus currentStatus;
    @Mock private RecordStreamManager recordStreamManager;

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private StatusChangeListener subject;

    @BeforeEach
    void setUp() {
        subject =
                new StatusChangeListener(currentStatus, new NodeId(false, 3L), recordStreamManager);
    }

    @Test
    void notifiesWhenActive() {
        given(notification.getNewStatus()).willReturn(ACTIVE);
        subject.notify(notification);
        assertTrue(
                logCaptor
                        .infoLogs()
                        .get(0)
                        .contains(
                                "Notification Received: Current Platform status changed to"
                                        + " ACTIVE"));
        assertTrue(
                logCaptor
                        .infoLogs()
                        .get(1)
                        .contains("Now current platform status = ACTIVE in HederaNode#3"));
        verify(currentStatus).set(ACTIVE);
        verify(recordStreamManager).setInFreeze(false);
    }

    @Test
    void notifiesWhenFrozen() {
        given(notification.getNewStatus()).willReturn(FREEZE_COMPLETE);
        subject.notify(notification);
        assertTrue(
                logCaptor
                        .infoLogs()
                        .get(0)
                        .contains(
                                "Notification Received: Current Platform status changed to"
                                        + " FREEZE_COMPLETE"));
        assertTrue(
                logCaptor
                        .infoLogs()
                        .get(1)
                        .contains("Now current platform status = FREEZE_COMPLETE in HederaNode#3"));
        verify(currentStatus).set(FREEZE_COMPLETE);
        verify(recordStreamManager).setInFreeze(true);
    }
}
