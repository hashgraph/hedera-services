/*
 * Copyright (C) 2018-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.sync;

import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.gossip.shadowgraph.ShadowEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.fixtures.sync.SyncNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SyncTestUtils {
    static final Logger log = LogManager.getLogger(SyncTestUtils.class);

    public static void printEvents(final String heading, final Collection<? extends EventImpl> events) {
        StringBuffer bf = new StringBuffer("\n--- " + heading + " ---");
        events.forEach(e -> bf.append(e.toString()).append("\n"));
        log.debug(bf.toString());
    }

    public static void printTasks(final String heading, final Collection<PlatformEvent> events) {
        StringBuffer bf = new StringBuffer("\n--- " + heading + " ---");
        events.forEach(e -> bf.append(e.toString()).append("\n"));
        log.debug(bf.toString());
    }

    public static void printTipSet(final String nodeName, final SyncNode node) {
        StringBuffer bf = new StringBuffer("---" + nodeName + "'s tipSet ---");
        node.getShadowGraph().getTips().forEach(tip -> bf.append(tip.getEvent().toString())
                .append("\n"));
        log.debug(bf.toString());
    }

    public static long getMaxIndicator(final List<ShadowEvent> tips, @NonNull final AncientMode ancientMode) {
        long maxIndicator = ancientMode.getGenesisIndicator();
        for (final ShadowEvent tip : tips) {
            maxIndicator = Math.max(tip.getEvent().getAncientIndicator(ancientMode), maxIndicator);
        }
        return maxIndicator;
    }

    public static long getMinIndicator(@NonNull final Set<ShadowEvent> events, @NonNull final AncientMode ancientMode) {
        long minIndicator = Long.MAX_VALUE;
        for (final ShadowEvent event : events) {
            minIndicator = Math.min(event.getEvent().getAncientIndicator(ancientMode), minIndicator);
        }
        return minIndicator == Long.MAX_VALUE ? ancientMode.getGenesisIndicator() : minIndicator;
    }
}
