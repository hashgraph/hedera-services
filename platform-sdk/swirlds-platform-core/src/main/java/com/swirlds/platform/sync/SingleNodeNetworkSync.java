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

import static com.swirlds.base.ArgumentUtils.throwArgNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * An {@link InterruptableRunnable} that imitates sync on a single node network
 */
public class SingleNodeNetworkSync implements InterruptableRunnable {
    /**
     * A runnable which kicks off the periodic reevaluation of the platform status
     */
    private final Runnable statusChecker;

    /**
     * A method which accepts a node ID and creates an event
     */
    private final LongConsumer eventCreator;

    /**
     * A supplier of the amount of time to sleep after creating an event (milliseconds)
     */
    private final LongSupplier sleepTimeSupplier;

    /**
     * The id of the single running node
     */
    private final long selfId;

    /**
     * Constructor
     *
     * @param statusChecker     runnable to check status of the platform
     * @param eventCreator      method which accepts a node ID and creates an event
     * @param sleepTimeSupplier supplier of the amount of time to sleep after creating an event (milliseconds)
     * @param selfId            the id of the single running node
     */
    public SingleNodeNetworkSync(
            @NonNull final Runnable statusChecker,
            @NonNull final LongConsumer eventCreator,
            @NonNull final LongSupplier sleepTimeSupplier,
            final long selfId) {

        this.statusChecker = throwArgNull(statusChecker, "statusChecker");
        this.eventCreator = throwArgNull(eventCreator, "eventCreator");
        this.sleepTimeSupplier = throwArgNull(sleepTimeSupplier, "sleepTimeSupplier");
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
        eventCreator.accept(selfId);

        final long sleepTime = sleepTimeSupplier.getAsLong();
        if (sleepTime > 0) {
            MILLISECONDS.sleep(sleepTime);
        } else {
            // if no sleep time is defined, sleep for a short time anyway
            MILLISECONDS.sleep(50);
        }
    }
}
