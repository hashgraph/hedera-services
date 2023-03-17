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

package com.swirlds.platform.test;

import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.event.RandomEventUtils;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

@Deprecated
public class SimpleEventGenerator {
    final int numberOfNodes;
    final EventImpl[] lastEvent;
    final Random random;
    final Set<Integer> excludeAsOtherParent;

    public SimpleEventGenerator(int numberOfNodes, Random random) {
        this.numberOfNodes = numberOfNodes;
        lastEvent = new EventImpl[numberOfNodes];
        this.random = random;
        excludeAsOtherParent = new HashSet<>();
    }

    public EventImpl nextEvent(final boolean fakeHash) {
        final int nodeId = random.nextInt(numberOfNodes);
        final int otherId = getOtherParent(nodeId);
        final EventImpl event =
                RandomEventUtils.randomEvent(random, nodeId, lastEvent[nodeId], lastEvent[otherId], fakeHash, true);
        lastEvent[nodeId] = event;
        return event;
    }

    public EventImpl nextEvent() {
        return nextEvent(true);
    }

    private int getOtherParent(int exclude) {
        int otherId = exclude;
        while (otherId == exclude || excludeAsOtherParent.contains(otherId)) {
            otherId = random.nextInt(numberOfNodes);
        }
        return otherId;
    }

    public void excludeOtherParent(int id) {
        excludeAsOtherParent.add(id);
    }

    public void includeOtherParent(int id) {
        excludeAsOtherParent.remove(id);
    }
}
