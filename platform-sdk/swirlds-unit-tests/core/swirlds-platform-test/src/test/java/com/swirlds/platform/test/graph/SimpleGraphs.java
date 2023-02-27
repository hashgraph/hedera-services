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
package com.swirlds.platform.test.graph;

import com.swirlds.platform.EventStrings;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.event.EventBuilder;
import java.util.List;
import java.util.Random;

public class SimpleGraphs {
    /**
     * Builds graph below:
     *
     * <pre>
     * 3  4
     * | /|
     * 2  |
     * | \|
     * 0  1
     * </pre>
     */
    public static List<GossipEvent> graph5e2n(final Random r) {
        final GossipEvent e0 =
                EventBuilder.builder().setRandom(r).setCreatorId(1).buildGossipEvent();
        final GossipEvent e1 =
                EventBuilder.builder().setRandom(r).setCreatorId(2).buildGossipEvent();
        final GossipEvent e2 =
                EventBuilder.builder()
                        .setRandom(r)
                        .setCreatorId(1)
                        .setSelfParent(e0)
                        .setOtherParent(e1)
                        .buildGossipEvent();
        final GossipEvent e3 =
                EventBuilder.builder()
                        .setRandom(r)
                        .setCreatorId(1)
                        .setSelfParent(e2)
                        .buildGossipEvent();
        final GossipEvent e4 =
                EventBuilder.builder()
                        .setRandom(r)
                        .setCreatorId(2)
                        .setSelfParent(e1)
                        .setOtherParent(e2)
                        .buildGossipEvent();
        System.out.println("e0 " + EventStrings.toShortString(e0));
        System.out.println("e1 " + EventStrings.toShortString(e1));
        System.out.println("e2 " + EventStrings.toShortString(e2));
        System.out.println("e3 " + EventStrings.toShortString(e3));
        System.out.println("e4 " + EventStrings.toShortString(e4));
        return List.of(e0, e1, e2, e3, e4);
    }

    /**
     * Builds the graph below:
     *
     * <pre>
     *       8
     *     / |
     * 5  6  7
     * | /| /|
     * 3  | |4
     * | \|/ |
     * 0  1  2
     *
     * Consensus events: 0,1
     *
     * Time created for events:
     * 0: 2020-05-06T13:21:56.680Z
     * 1: 2020-05-06T13:21:56.681Z
     * 2: 2020-05-06T13:21:56.682Z
     * 3: 2020-05-06T13:21:56.683Z
     * 4: 2020-05-06T13:21:56.686Z
     * 5: 2020-05-06T13:21:56.685Z
     * 6: 2020-05-06T13:21:56.686Z
     * 7: 2020-05-06T13:21:56.690Z
     * 8: 2020-05-06T13:21:56.694Z
     *
     * </pre>
     */
    public static List<EventImpl> graph9e3n(final Random r) {
        // generation 0
        final EventImpl e0 =
                EventBuilder.builder()
                        .setRandom(r)
                        .setCreatorId(1)
                        .setConsensus(true)
                        .buildEventImpl();
        final EventImpl e1 =
                EventBuilder.builder()
                        .setRandom(r)
                        .setCreatorId(2)
                        .setConsensus(true)
                        .buildEventImpl();
        final EventImpl e2 = EventBuilder.builder().setRandom(r).setCreatorId(3).buildEventImpl();
        // generation 1
        final EventImpl e3 =
                EventBuilder.builder()
                        .setRandom(r)
                        .setCreatorId(1)
                        .setSelfParent(e0)
                        .setOtherParent(e1)
                        .buildEventImpl();
        final EventImpl e4 =
                EventBuilder.builder()
                        .setRandom(r)
                        .setCreatorId(3)
                        .setSelfParent(e2)
                        .buildEventImpl();
        // generation 2
        final EventImpl e5 =
                EventBuilder.builder()
                        .setRandom(r)
                        .setCreatorId(1)
                        .setSelfParent(e3)
                        .buildEventImpl();
        final EventImpl e6 =
                EventBuilder.builder()
                        .setRandom(r)
                        .setCreatorId(2)
                        .setSelfParent(e1)
                        .setOtherParent(e3)
                        .buildEventImpl();
        final EventImpl e7 =
                EventBuilder.builder()
                        .setRandom(r)
                        .setCreatorId(3)
                        .setSelfParent(e4)
                        .setOtherParent(e1)
                        .buildEventImpl();
        // generation 3
        final EventImpl e8 =
                EventBuilder.builder()
                        .setRandom(r)
                        .setCreatorId(3)
                        .setSelfParent(e7)
                        .setOtherParent(e6)
                        .buildEventImpl();
        return List.of(e0, e1, e2, e3, e4, e5, e6, e7, e8);
    }
}
