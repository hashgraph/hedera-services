/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.gossip.shadowgraph.ShadowEvent;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class SyncTestUtils {

    public static void printEvents(final String heading, final Collection<? extends EventImpl> events) {
        System.out.println("\n--- " + heading + " ---");
        events.forEach(System.out::println);
    }

    public static void printTasks(final String heading, final Collection<PlatformEvent> events) {
        System.out.println("\n--- " + heading + " ---");
        events.forEach(System.out::println);
    }

    public static void printTipSet(final String nodeName, final SyncNode node) {
        System.out.printf("\n--- %s's tipSet ---%n", nodeName);
        node.getShadowGraph().getTips().forEach(tip -> System.out.println(tip.getEvent()));
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
