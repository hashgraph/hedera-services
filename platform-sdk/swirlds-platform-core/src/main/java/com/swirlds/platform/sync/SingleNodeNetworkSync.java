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

package com.swirlds.platform.sync;

import com.swirlds.base.ArgumentUtils;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.platform.components.EventTaskCreator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * An {@link InterruptableRunnable} that imitates sync on a single node network
 */
public class SingleNodeNetworkSync implements InterruptableRunnable {
    /**
     * A runnable which kicks off the periodic reevaluation of the platform status
     */
    private final Runnable statusChecker;

    /**
     * A supplier of the event task creator
     */
    private final Supplier<EventTaskCreator> eventTaskCreatorSupplier;

    /**
     * A supplier of the amount of time to sleep after creating an event
     */
    private final LongSupplier sleepTimeSupplier;

    /**
     * The id of the single running node
     */
    private final long selfId;

    /**
     * Constructor
     *
     * @param statusChecker            runnable to check status of the platform
     * @param eventTaskCreatorSupplier supplier of event task creator
     * @param sleepTimeSupplier        supplier of the amount of time to sleep after creating an event
     * @param selfId                   the id of the single running node
     */
    public SingleNodeNetworkSync(
            @NonNull final Runnable statusChecker,
            @NonNull final Supplier<EventTaskCreator> eventTaskCreatorSupplier,
            @NonNull final LongSupplier sleepTimeSupplier,
            final long selfId) {

        ArgumentUtils.throwArgNull(statusChecker, "statusChecker");
        ArgumentUtils.throwArgNull(eventTaskCreatorSupplier, "eventTaskCreatorSupplier");
        ArgumentUtils.throwArgNull(sleepTimeSupplier, "sleepTimeSupplier");

        this.statusChecker = statusChecker;
        this.eventTaskCreatorSupplier = eventTaskCreatorSupplier;
        this.sleepTimeSupplier = sleepTimeSupplier;
        this.selfId = selfId;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Checks platform status, and then creates an event which is immediately added to the hashgraph. This is like
     * "syncing" with self, and then creating an event with otherParent being self.
     * <p>
     * After creating the event, this method sleeps for the amount of time returned by the sleep time supplier, or 50
     * milliseconds if returned duration is 0
     */
    @Override
    public void run() throws InterruptedException {
        statusChecker.run();
        eventTaskCreatorSupplier.get().createEvent(selfId);

        final long sleepTime = sleepTimeSupplier.getAsLong();
        if (sleepTime > 0) {
            Thread.sleep(sleepTime);
        } else {
            // if no sleep time is defined, sleep for a short time anyway
            Thread.sleep(50);
        }
    }
}
