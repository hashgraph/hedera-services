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

package com.swirlds.platform.test.sync;

import com.swirlds.platform.EventStrings;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.shadowgraph.ShadowEvent;
import com.swirlds.platform.internal.EventImpl;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class SyncUtils {

    public static void printEvents(final String heading, final Collection<? extends EventImpl> events) {
        System.out.println("\n--- " + heading + " ---");
        events.forEach(e -> System.out.println(EventStrings.toMediumString(e)));
    }

    public static void printTasks(final String heading, final Collection<GossipEvent> events) {
        System.out.println("\n--- " + heading + " ---");
        events.forEach(e -> System.out.println(EventStrings.toMediumString(e)));
    }

    public static void printTipSet(final String nodeName, final SyncNode node) {
        System.out.printf("\n--- %s's tipSet ---%n", nodeName);
        node.getShadowGraph().getTips().forEach(tip -> System.out.println(EventStrings.toMediumString(tip.getEvent())));
    }

    public static long getMaxGen(final List<ShadowEvent> tips) {
        long maxGen = 0;
        for (ShadowEvent tip : tips) {
            maxGen = Math.max(tip.getEvent().getGeneration(), maxGen);
        }
        return maxGen;
    }

    public static long getMinGen(final Set<ShadowEvent> events) {
        long minGen = Long.MAX_VALUE;
        for (ShadowEvent event : events) {
            minGen = Math.min(event.getEvent().getGeneration(), minGen);
        }
        return minGen == Long.MAX_VALUE ? 0 : minGen;
    }
}
