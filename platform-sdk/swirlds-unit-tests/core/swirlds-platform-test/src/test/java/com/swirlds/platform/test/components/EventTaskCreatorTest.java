/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.config.EventConfig;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.components.EventMapper;
import com.swirlds.platform.components.EventTaskCreator;
import com.swirlds.platform.event.CreateEventTask;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.shadowgraph.SyncResult;
import com.swirlds.platform.gossip.sync.SyncManager;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import com.swirlds.test.framework.config.TestConfigBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EventTaskCreatorTest {

    EventMapper eventMapper;
    AddressBook addressBook;
    Address address;
    NodeId selfId;
    EventIntakeMetrics eventIntakeMetrics;
    BlockingQueue<EventIntakeTask> eventQueueThread;
    SyncManager syncManager;
    EventTaskCreator taskCreator;
    Random random;

    private void init() {
        final var config = new TestConfigBuilder().getOrCreateConfig().getConfigData(EventConfig.class);
        init(config);
    }

    private void init(final EventConfig config) {
        eventMapper = mock(EventMapper.class);
        addressBook = prepareAddressBook();
        address = mock(Address.class);
        selfId = addressBook.getNodeId(addressBook.getSize() - 1);
        eventIntakeMetrics = mock(EventIntakeMetrics.class);
        eventQueueThread = mock(BlockingQueue.class);
        syncManager = mock(SyncManager.class);
        random = new MockRandom();
        taskCreator = new EventTaskCreator(
                eventMapper,
                addressBook,
                selfId,
                eventIntakeMetrics,
                eventQueueThread,
                config,
                syncManager,
                () -> random);
    }

    private AddressBook prepareAddressBook() {
        // this is a work around instead of refactoring the whole unit test file.
        // the implementation of rescue children now iterates over the addresses in the address book.
        return new RandomAddressBookGenerator().setSize(5).build();
    }

    @NonNull
    private EventConfig configRandomEventProbability() {
        return new TestConfigBuilder()
                .withValue("event.randomEventProbability", 1)
                .getOrCreateConfig()
                .getConfigData(EventConfig.class);
    }

    @NonNull
    private EventConfig configRescueChildlessInverseProbability(final int value) {
        return new TestConfigBuilder()
                .withValue("event.rescueChildlessInverseProbability", value)
                .getOrCreateConfig()
                .getConfigData(EventConfig.class);
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("test createEvent()")
    void testCreateEvent() throws InterruptedException {
        init();
        final NodeId otherId = new NodeId(7);

        // regular call
        taskCreator.createEvent(otherId);
        ArgumentCaptor<CreateEventTask> captor = ArgumentCaptor.forClass(CreateEventTask.class);
        verify(eventQueueThread).put(captor.capture());
        assertEquals(
                otherId, captor.getValue().getOtherId(), "otherId should be same in the task as the one passed in");

        reset(eventQueueThread);

        // with zero weight node
        when(address.isZeroWeight()).thenReturn(true);
        taskCreator.createEvent(otherId);
        verify(eventQueueThread, times(1)).put(any());
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("test addEvent()")
    void testAddEvent() throws InterruptedException {
        init();
        GossipEvent task = new GossipEvent(mock(BaseEventHashedData.class), mock(BaseEventUnhashedData.class));
        taskCreator.addEvent(task);
        verify(eventQueueThread).put(task);
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("test addEvent()")
    void testEventRescue() throws InterruptedException {
        init(configRescueChildlessInverseProbability(5));

        EventImpl eventToRescue = mock(EventImpl.class);
        when(eventToRescue.getCreatorId()).thenReturn(addressBook.getNodeId(2));
        when(eventMapper.getMostRecentEvent(eventToRescue.getCreatorId())).thenReturn(eventToRescue);

        taskCreator.rescueChildlessEvents();

        ArgumentCaptor<CreateEventTask> captor = ArgumentCaptor.forClass(CreateEventTask.class);
        verify(eventQueueThread).put(captor.capture());
        assertEquals(
                eventToRescue.getCreatorId(),
                captor.getValue().getOtherId(),
                "otherId should match the senderId of the rescued event");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("test addEvent()")
    void testEventNotRescue() throws InterruptedException {
        init(configRescueChildlessInverseProbability(0));

        final EventImpl eventToRescue = mock(EventImpl.class);
        when(eventToRescue.getCreatorId()).thenReturn(addressBook.getNodeId(2));
        when(eventMapper.getMostRecentEvent(eventToRescue.getCreatorId())).thenReturn(eventToRescue);

        taskCreator.rescueChildlessEvents();

        verify(eventQueueThread, times(0)).put(any());
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("test syncDone()")
    void testSyncDone_ShouldNotCreateEvent() {
        init();
        when(syncManager.shouldCreateEvent(any())).thenReturn(false);

        taskCreator.syncDone(mock(SyncResult.class));

        verifyNoInteractions(eventQueueThread);
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("test syncDone()")
    void testSyncDone_ShouldCreateEvent() throws InterruptedException {
        init();
        when(syncManager.shouldCreateEvent(any())).thenReturn(true);

        SyncResult syncResult = mock(SyncResult.class);
        when(syncResult.getOtherId()).thenReturn(new NodeId(2));

        taskCreator.syncDone(syncResult);

        verify(eventQueueThread, atLeastOnce()).put(any());
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("test syncDone randomEvent")
    void testSyncDoneRandomEvent() throws InterruptedException {
        init(configRandomEventProbability());
        when(syncManager.shouldCreateEvent(any())).thenReturn(true);

        SyncResult syncResult = mock(SyncResult.class);
        when(syncResult.getOtherId()).thenReturn(new NodeId(2));

        taskCreator.syncDone(syncResult);

        verify(eventQueueThread, times(2)).put(any());
    }
}
