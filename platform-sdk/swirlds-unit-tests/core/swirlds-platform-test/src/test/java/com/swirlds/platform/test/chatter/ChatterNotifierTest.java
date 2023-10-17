/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.chatter;

import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.chatter.ChatterNotifier;
import com.swirlds.platform.gossip.chatter.protocol.ChatterCore;
import com.swirlds.platform.gossip.shadowgraph.Generations;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatterNotifierTest {

    @Mock
    ChatterCore<GossipEvent> chatterCore;

    @Mock
    GossipEvent gossipEvent;

    @Mock
    EventImpl event;

    @InjectMocks
    ChatterNotifier notifier;

    @Test
    void testEventAdded() {
        Mockito.when(event.getBaseEvent()).thenReturn(gossipEvent);
        Mockito.when(event.isCreatedBy(any())).thenReturn(true);
        notifier.eventAdded(event);
        verify(chatterCore).eventCreated(gossipEvent);

        Mockito.when(event.isCreatedBy(any())).thenReturn(false);
        notifier.eventAdded(event);
        verify(chatterCore).eventReceived(gossipEvent);
    }

    @Test
    @Tag(TIME_CONSUMING)
    void testPurge() {
        notifier.consensusRound(new ConsensusRound(
                List.of(event), mock(EventImpl.class), new Generations(1, 2, 3), mock(ConsensusSnapshot.class)));
        verify(chatterCore).shiftWindow(1);
    }
}
