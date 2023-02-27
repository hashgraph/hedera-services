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

package com.swirlds.platform.test.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.platform.SettingsProvider;
import com.swirlds.platform.components.EventMapper;
import com.swirlds.platform.components.EventTaskCreator;
import com.swirlds.platform.event.CreateEventTask;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import com.swirlds.platform.sync.SyncManager;
import com.swirlds.platform.sync.SyncResult;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class EventTaskCreatorTest {

    EventMapper eventMapper;
    AddressBook addressBook;
    Address address;
    NodeId selfId;
    EventIntakeMetrics eventIntakeMetrics;
    BlockingQueue<EventIntakeTask> eventQueueThread;
    SettingsProvider setting;
    SyncManager syncManager;
    EventTaskCreator taskCreator;
    Random random;

    @BeforeEach
    void newMocks() {
        eventMapper = mock(EventMapper.class);
        addressBook = mock(AddressBook.class);
        address = mock(Address.class);
        when(addressBook.getAddress(Mockito.anyLong())).thenReturn(address);
        when(addressBook.copy()).thenReturn(addressBook);
        selfId = NodeId.createMain(1);
        eventIntakeMetrics = mock(EventIntakeMetrics.class);
        eventQueueThread = mock(BlockingQueue.class);
        setting = mock(SettingsProvider.class);
        syncManager = mock(SyncManager.class);
        random = new MockRandom();
        taskCreator = new EventTaskCreator(
                eventMapper,
                addressBook,
                selfId,
                eventIntakeMetrics,
                eventQueueThread,
                setting,
                syncManager,
                () -> random);
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("test createEvent()")
    void testCreateEvent() throws InterruptedException {
        final int otherId = 7;

        // regular call
        taskCreator.createEvent(otherId);
        ArgumentCaptor<CreateEventTask> captor = ArgumentCaptor.forClass(CreateEventTask.class);
        verify(eventQueueThread).put(captor.capture());
        assertEquals(
                otherId, captor.getValue().getOtherId(), "otherId should be same in the task as the one passed in");

        reset(eventQueueThread);

        // with zero stake node
        when(address.isZeroStake()).thenReturn(true);
        taskCreator.createEvent(otherId);
        verify(eventQueueThread, times(0)).put(any());
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("test addEvent()")
    void testAddEvent() throws InterruptedException {
        GossipEvent task = new GossipEvent(mock(BaseEventHashedData.class), mock(BaseEventUnhashedData.class));
        taskCreator.addEvent(task);
        verify(eventQueueThread).put(task);
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("test addEvent()")
    void testEventRescue() throws InterruptedException {
        when(setting.getRescueChildlessInverseProbability()).thenReturn(5);
        when(addressBook.getSize()).thenReturn(5);
        EventImpl eventToRescue = mock(EventImpl.class);
        when(eventToRescue.getCreatorId()).thenReturn(2L);
        when(eventMapper.getMostRecentEvent(eventToRescue.getCreatorId())).thenReturn(eventToRescue);

        taskCreator.rescueChildlessEvents();

        ArgumentCaptor<CreateEventTask> captor = ArgumentCaptor.forClass(CreateEventTask.class);
        verify(eventQueueThread).put(captor.capture());
        assertEquals(
                eventToRescue.getCreatorId(),
                captor.getValue().getOtherId(),
                "otherId should match the creatorId of the rescued event");

        reset(eventQueueThread);

        // test with feature off
        when(setting.getRescueChildlessInverseProbability()).thenReturn(0);
        taskCreator.rescueChildlessEvents();
        verify(eventQueueThread, times(0)).put(any());
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("test syncDone()")
    void testSyncDone_ShouldNotCreateEvent() {
        when(syncManager.shouldCreateEvent(any())).thenReturn(false);

        taskCreator.syncDone(mock(SyncResult.class));

        verifyNoInteractions(eventQueueThread);
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("test syncDone()")
    void testSyncDone_ShouldCreateEvent() throws InterruptedException {
        when(syncManager.shouldCreateEvent(any())).thenReturn(true);

        SyncResult syncResult = mock(SyncResult.class);
        when(syncResult.getOtherId()).thenReturn(mock(NodeId.class));

        taskCreator.syncDone(syncResult);

        verify(eventQueueThread, atLeastOnce()).put(any());
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("test syncDone randomEvent")
    void testSyncDoneRandomEvent() throws InterruptedException {
        when(syncManager.shouldCreateEvent(any())).thenReturn(true);
        when(setting.getRandomEventProbability()).thenReturn(1);

        SyncResult syncResult = mock(SyncResult.class);
        when(syncResult.getOtherId()).thenReturn(mock(NodeId.class));

        taskCreator.syncDone(syncResult);

        verify(eventQueueThread, times(2)).put(any());
    }
}
