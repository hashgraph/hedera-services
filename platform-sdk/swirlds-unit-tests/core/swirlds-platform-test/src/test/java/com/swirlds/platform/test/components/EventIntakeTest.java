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

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.extensions.PhaseTimer;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.components.EventIntake;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.linking.EventLinker;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.shadowgraph.Generations;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EventIntakeTest {

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("Test intake")
    void test() {
        final EventObserverDispatcher dispatcher = mock(EventObserverDispatcher.class);
        final Consensus consensus = mock(Consensus.class);
        final AddressBook addressBook = mock(AddressBook.class);
        final ArgumentCaptor<ConsensusRound> roundCaptor = ArgumentCaptor.forClass(ConsensusRound.class);
        final ShadowGraph shadowGraph = mock(ShadowGraph.class);

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final EventIntake intake = new EventIntake(
                platformContext,
                getStaticThreadManager(),
                Time.getCurrent(),
                new NodeId(0L),
                mock(EventLinker.class),
                () -> consensus,
                addressBook,
                dispatcher,
                mock(PhaseTimer.class),
                shadowGraph,
                e -> {},
                mock(IntakeEventCounter.class));

        final GossipEvent gossipEvent = mock(GossipEvent.class);
        final EventImpl added = mock(EventImpl.class);
        when(added.getBaseEvent()).thenReturn(gossipEvent);
        final EventImpl consEvent1 = mock(EventImpl.class);
        final EventImpl consEvent2 = mock(EventImpl.class);
        final EventImpl stale = mock(EventImpl.class);

        final Queue<EventImpl> staleQueue = new LinkedList<>(List.of(stale));
        when(shadowGraph.findByGeneration(anyLong(), anyLong(), any())).thenReturn(staleQueue);
        final AtomicLong minNonAncient = new AtomicLong(10);
        final Generations generations =
                new Generations(minNonAncient.get() - 1, minNonAncient.get(), minNonAncient.get() + 1);
        when(consensus.getMinRoundGeneration()).thenAnswer(i -> minNonAncient.get() - 1);
        when(consensus.getMinGenerationNonAncient()).thenAnswer(i -> minNonAncient.get());
        when(consensus.getMaxRoundGeneration()).thenAnswer(i -> minNonAncient.get() + 1);
        when(consensus.addEvent(any(EventImpl.class))).thenAnswer(i -> {
            minNonAncient.set(20);
            return List.of(new ConsensusRound(
                    List.of(consEvent1, consEvent2), added, generations, mock(ConsensusSnapshot.class)));
        });

        // add an event
        intake.addEvent(added);

        // verify
        verify(dispatcher).preConsensusEvent(added);
        verify(dispatcher).eventAdded(added);
        verify(dispatcher).consensusRound(roundCaptor.capture());
        assertEquals(1, roundCaptor.getAllValues().size());
        assertTrue(roundCaptor.getValue().getConsensusEvents().contains(consEvent1));
        assertTrue(roundCaptor.getValue().getConsensusEvents().contains(consEvent2));
        verify(dispatcher).staleEvent(stale);

        verify(consensus).addEvent(added);

        // cover other conditions
        when(consensus.addEvent(any(EventImpl.class))).thenReturn(null);
        intake.addEvent(added);
        verify(dispatcher, times(1)).consensusRound(roundCaptor.getValue());
    }
}
