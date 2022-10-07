/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.tasks;

import static com.hedera.services.state.merkle.MerkleNetworkContext.*;
import static com.hedera.services.state.tasks.SystemTaskResult.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.hedera.services.state.merkle.MerkleNetworkContext;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemTaskManagerTest {
    private static final long ENTITY_NUM = 1234L;
    private static final Instant NOW = Instant.ofEpochSecond(1_234_567, 890);

    @Mock private SystemTask aTask;
    @Mock private SystemTask bTask;
    @Mock private SystemTask cTask;
    @Mock private MerkleNetworkContext networkCtx;

    private SystemTaskManager subject;

    @BeforeEach
    void setUp() {
        subject = new SystemTaskManager(Map.of("C", cTask, "B", bTask, "A", aTask));
    }

    @Test
    void scanUpdatesAllScannedIfLastIsScannedAndNumReachesPostUpgradeLastScanned() {
        final var lastScanned = 1234L;
        given(networkCtx.getPreExistingEntityScanStatus())
                .willReturn(LAST_PRE_EXISTING_ENTITY_SCANNED);
        given(networkCtx.lastScannedPostUpgrade()).willReturn(lastScanned);

        subject.updatePreExistingScanStatus(lastScanned, networkCtx);

        verify(networkCtx).setPreExistingEntityScanStatus(ALL_PRE_EXISTING_ENTITIES_SCANNED);
    }

    @Test
    void scanUpdatesLastScannedIfNumPrecedesPostUpgradeSeqNo() {
        final var seqNo = 1234L;
        given(networkCtx.getPreExistingEntityScanStatus())
                .willReturn(LAST_PRE_EXISTING_ENTITY_NOT_SCANNED);
        given(networkCtx.seqNoPostUpgrade()).willReturn(seqNo);

        subject.updatePreExistingScanStatus(seqNo - 1, networkCtx);

        verify(networkCtx).setPreExistingEntityScanStatus(LAST_PRE_EXISTING_ENTITY_SCANNED);
    }

    @Test
    void scanUpdateNoopIfAlreadyComplete() {
        given(networkCtx.getPreExistingEntityScanStatus())
                .willReturn(ALL_PRE_EXISTING_ENTITIES_SCANNED);

        subject.updatePreExistingScanStatus(ENTITY_NUM, networkCtx);

        verify(networkCtx).getPreExistingEntityScanStatus();
        verifyNoMoreInteractions(networkCtx);
    }

    @Test
    void defaultTaskIsActive() {
        final var someTask = mock(SystemTask.class);
        doCallRealMethod().when(someTask).isActive(ENTITY_NUM, networkCtx);
        assertTrue(someTask.isActive(ENTITY_NUM, networkCtx));
    }

    @Test
    void ordersTasksAlphabetically() {
        assertArrayEquals(new SystemTask[] {aTask, bTask, cTask}, subject.getTasks());
    }

    @Test
    void abortsOnNeedsDifferentContextWithoutGoingToNextTask() {
        given(networkCtx.nextTaskTodo()).willReturn(0);
        givenActive(aTask, bTask, cTask);
        given(aTask.process(ENTITY_NUM, NOW)).willReturn(DONE);
        given(bTask.process(ENTITY_NUM, NOW)).willReturn(NOTHING_TO_DO);
        given(cTask.process(ENTITY_NUM, NOW)).willReturn(NEEDS_DIFFERENT_CONTEXT);

        final var result = subject.process(ENTITY_NUM, NOW, networkCtx);
        assertEquals(NEEDS_DIFFERENT_CONTEXT, result);
        verify(networkCtx).setNextTaskTodo(2);
    }

    @Test
    void abortsOnCapacityExhaustedWithoutGoingToNextTask() {
        given(networkCtx.nextTaskTodo()).willReturn(0);
        givenActive(bTask);
        given(bTask.process(ENTITY_NUM, NOW)).willReturn(NO_CAPACITY_LEFT);

        final var result = subject.process(ENTITY_NUM, NOW, networkCtx);
        assertEquals(NO_CAPACITY_LEFT, result);
        verify(networkCtx).setNextTaskTodo(1);
    }

    @Test
    void nothingToDoOnlyIfNothingDone() {
        given(networkCtx.nextTaskTodo()).willReturn(1);
        givenActive(bTask, cTask);
        given(bTask.process(ENTITY_NUM, NOW)).willReturn(NOTHING_TO_DO);
        given(cTask.process(ENTITY_NUM, NOW)).willReturn(NOTHING_TO_DO);

        final var result = subject.process(ENTITY_NUM, NOW, networkCtx);
        assertEquals(NOTHING_TO_DO, result);
        verify(networkCtx).setNextTaskTodo(0);
    }

    @Test
    void doneIfAnythingWasDone() {
        given(networkCtx.nextTaskTodo()).willReturn(0);
        givenActive(bTask, cTask);
        given(bTask.process(ENTITY_NUM, NOW)).willReturn(DONE);
        given(cTask.process(ENTITY_NUM, NOW)).willReturn(NOTHING_TO_DO);

        final var result = subject.process(ENTITY_NUM, NOW, networkCtx);
        assertEquals(DONE, result);
        verify(networkCtx).setNextTaskTodo(0);
    }

    private void givenActive(final SystemTask... tasks) {
        for (final var task : tasks) {
            given(task.isActive(ENTITY_NUM, networkCtx)).willReturn(true);
        }
    }
}
