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

package com.swirlds.platform.test.consensus;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.ConsensusImpl;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.sync.ShadowGraph;
import com.swirlds.platform.sync.ShadowGraphInsertionException;
import java.util.List;
import java.util.function.BiConsumer;

public class ConsensusWithShadowGraph extends ConsensusImpl {

    private static final BiConsumer<Long, Long> NOOP_MINGEN = (l1, l2) -> {};

    private final ShadowGraph shadowGraph;

    public ConsensusWithShadowGraph(final AddressBook addressBook, final BiConsumer<Long, Long> minGenConsumer) {
        super(
                ConfigurationHolder.getConfigData(ConsensusConfig.class),
                ConsensusUtils.NOOP_CONSENSUS_METRICS,
                minGenConsumer,
                addressBook);
        shadowGraph = new ShadowGraph(mock(SyncMetrics.class));
    }

    public ConsensusWithShadowGraph(final AddressBook addressBook, final SignedState signedState) {
        super(
                ConfigurationHolder.getConfigData(ConsensusConfig.class),
                ConsensusUtils.NOOP_CONSENSUS_METRICS,
                NOOP_MINGEN,
                addressBook,
                signedState);
        shadowGraph = new ShadowGraph(mock(SyncMetrics.class));
        // consensus will not return old events, so these are the ones we need to add to shadowgraph
        shadowGraph.initFromEvents(EventUtils.prepareForShadowGraph(signedState.getEvents()), getMinRoundGeneration());
    }

    @Override
    public List<EventImpl> addEvent(EventImpl event, AddressBook addressBook) {
        try {
            shadowGraph.addEvent(event);
        } catch (ShadowGraphInsertionException e) {
            fail(String.format("Failed to insert event %s into the shadow graph", event.toShortString()), e);
        }
        return super.addEvent(event, addressBook);
    }

    public ShadowGraph getShadowGraph() {
        return shadowGraph;
    }
}
