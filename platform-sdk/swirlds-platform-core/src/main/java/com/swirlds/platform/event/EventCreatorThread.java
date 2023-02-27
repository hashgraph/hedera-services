/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event;

import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.BooleanFunction;
import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.config.ThreadConfig;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * An independent thread that creates new events periodically
 */
public class EventCreatorThread implements Clearable {
    private final StoppableThread creatorThread;
    private final List<NodeId> otherNodes;
    private final Random random;
    private final NodeId selfId;
    private final BooleanFunction<Long> eventCreator;

    /**
     * @param threadManager
     * 		responsible for managing thread lifecycles
     * @param selfId
     * 		the ID of this node
     * @param attemptedChatterEventPerSecond
     * 		the desired number of events created per second
     * @param addressBook
     * 		the node's address book
     * @param eventCreator
     * 		this method attempts to create an event with a neighbor
     */
    public EventCreatorThread(
            final ThreadManager threadManager,
            final NodeId selfId,
            final int attemptedChatterEventPerSecond,
            final AddressBook addressBook,
            final BooleanFunction<Long> eventCreator,
            final Random random) {
        this.selfId = selfId;
        this.eventCreator = eventCreator;
        this.random = random;
        this.otherNodes = StreamSupport.stream(addressBook.spliterator(), false)
                // don't create events with self as other parent
                .filter(a -> !selfId.equalsMain(a.getId()))
                .map(a -> NodeId.createMain(a.getId()))
                .collect(Collectors.toList());

        creatorThread = new StoppableThreadConfiguration<>(threadManager)
                .setPriority(Thread.NORM_PRIORITY)
                .setNodeId(selfId.getId())
                .setMaximumRate(attemptedChatterEventPerSecond)
                .setComponent("Chatter")
                .setThreadName("EventGenerator")
                .setLogAfterPauseDuration(ConfigurationHolder.getInstance()
                        .get()
                        .getConfigData(ThreadConfig.class)
                        .logStackTracePauseDuration())
                .setWork(this::createEvent)
                .build();
    }

    public void createEvent() {
        // in case of a single node network, create events that have self as the other parent
        if (otherNodes.isEmpty()) {
            this.eventCreator.apply(selfId.getId());
            return;
        }
        Collections.shuffle(otherNodes, random);
        for (final NodeId neighbor : otherNodes) {
            if (this.eventCreator.apply(neighbor.getId())) {
                // try all neighbors until we create an event
                break;
            }
        }
    }

    public void start() {
        creatorThread.start();
    }

    /**
     * Pauses the thread and unpauses it again. This ensures that any event that was in the process of being created is
     * now done.
     */
    @Override
    public void clear() {
        creatorThread.pause();
        creatorThread.resume();
    }
}
