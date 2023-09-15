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

package com.swirlds.platform.components;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.platform.event.GossipEvent;
import java.util.concurrent.BlockingQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for creating and enqueuing event tasks. Event tasks can either be {@link GossipEvent} or TODO
 */
public class EventTaskCreator { // TODO this can probably die

    private static final Logger logger = LogManager.getLogger(EventTaskCreator.class);

    /** A {@link QueueThread} that handles event intake */
    private final BlockingQueue<GossipEvent> eventIntakeQueue;

    /**
     * Constructor.
     * <p>
     * tracks metrics
     *
     * @param eventIntakeQueue the queue add tasks to
     */
    public EventTaskCreator(final BlockingQueue<GossipEvent> eventIntakeQueue) {

        this.eventIntakeQueue = eventIntakeQueue;
    }

    /**
     * Add an event task to the queue to be instantiated by other threads in parallel. The instantiated event will
     * eventually be added to the hashgraph by the pollIntakeQueue method.
     *
     * @param intakeTask a task whose event is to be added to the hashgraph
     */
    public void addEvent(final GossipEvent intakeTask) {
        try {
            eventIntakeQueue.put(intakeTask);
        } catch (InterruptedException e) {
            // should never happen, and we don't have a simple way of recovering from it
            logger.error(
                    EXCEPTION.getMarker(), "CRITICAL ERROR, adding intakeTask to the event intake queue failed", e);
            Thread.currentThread().interrupt();
        }
    }
}
