/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.event.tipset;

import static com.swirlds.common.system.EventCreationRuleResponse.PASS;
import static com.swirlds.common.system.platformstatus.PlatformStatus.ACTIVE;
import static com.swirlds.common.system.platformstatus.PlatformStatus.CHECKING;
import static com.swirlds.common.system.platformstatus.PlatformStatus.FREEZING;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.platformstatus.PlatformStatus;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.platform.StartUpEventFrozenManager;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.event.tipset.ThrottledTipsetEventCreator;
import com.swirlds.platform.event.tipset.TipsetEventCreator;
import com.swirlds.platform.eventhandling.EventTransactionPool;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ThrottledTipsetEventCreator Tests")
class ThrottledTipsetEventCreatorTests {

    @Test
    @DisplayName("Blocked by StartUpFrozenManager Test")
    void blockedByStartUpFrozenManagerTest() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final EventTransactionPool transactionPool = mock(EventTransactionPool.class);
        final QueueThread<EventIntakeTask> eventIntakeQueue = mock(QueueThread.class);
        final Supplier<PlatformStatus> platformStatusSupplier = () -> ACTIVE;

        final AtomicReference<EventCreationRuleResponse> shouldCreateEvent =
                new AtomicReference<>(EventCreationRuleResponse.DONT_CREATE);
        final StartUpEventFrozenManager startUpEventFrozenManager = mock(StartUpEventFrozenManager.class);
        when(startUpEventFrozenManager.shouldCreateEvent()).thenAnswer(invocation -> shouldCreateEvent.get());

        final ThrottledTipsetEventCreator eventCreator = new ThrottledTipsetEventCreator(
                platformContext,
                Time.getCurrent(),
                transactionPool,
                eventIntakeQueue,
                platformStatusSupplier,
                startUpEventFrozenManager,
                mock(TipsetEventCreator.class));

        assertFalse(eventCreator.isEventCreationPermitted());

        shouldCreateEvent.set(PASS);

        assertTrue(eventCreator.isEventCreationPermitted());
    }

    @Test
    @DisplayName("Blocked by Freeze Test")
    void blockedByFreeze() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final QueueThread<EventIntakeTask> eventIntakeQueue = mock(QueueThread.class);
        final Supplier<PlatformStatus> platformStatusSupplier = () -> FREEZING;
        final StartUpEventFrozenManager startUpEventFrozenManager = mock(StartUpEventFrozenManager.class);
        when(startUpEventFrozenManager.shouldCreateEvent()).thenAnswer(invocation -> PASS);

        final AtomicInteger numSignatureTransactions = new AtomicInteger(0);
        final EventTransactionPool transactionPool = mock(EventTransactionPool.class);
        when(transactionPool.numSignatureTransEvent()).thenAnswer(invocation -> numSignatureTransactions.get());

        final ThrottledTipsetEventCreator eventCreator = new ThrottledTipsetEventCreator(
                platformContext,
                Time.getCurrent(),
                transactionPool,
                eventIntakeQueue,
                platformStatusSupplier,
                startUpEventFrozenManager,
                mock(TipsetEventCreator.class));

        assertFalse(eventCreator.isEventCreationPermitted());

        numSignatureTransactions.set(1);

        assertTrue(eventCreator.isEventCreationPermitted());
    }

    @Test
    @DisplayName("Blocked by Status Test")
    void blockedByStatus() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final EventTransactionPool transactionPool = mock(EventTransactionPool.class);
        final QueueThread<EventIntakeTask> eventIntakeQueue = mock(QueueThread.class);
        final StartUpEventFrozenManager startUpEventFrozenManager = mock(StartUpEventFrozenManager.class);
        when(startUpEventFrozenManager.shouldCreateEvent()).thenAnswer(invocation -> PASS);

        final AtomicReference<PlatformStatus> status = new AtomicReference<>();

        final ThrottledTipsetEventCreator eventCreator = new ThrottledTipsetEventCreator(
                platformContext,
                Time.getCurrent(),
                transactionPool,
                eventIntakeQueue,
                status::get,
                startUpEventFrozenManager,
                mock(TipsetEventCreator.class));

        for (final PlatformStatus platformStatus : PlatformStatus.values()) {
            if (platformStatus == FREEZING) {
                // this is checked in another test, don't bother checking
                continue;
            }

            status.set(platformStatus);

            if (platformStatus == ACTIVE || platformStatus == CHECKING) {
                assertTrue(eventCreator.isEventCreationPermitted());
            } else {
                assertFalse(eventCreator.isEventCreationPermitted());
            }
        }
    }
}
