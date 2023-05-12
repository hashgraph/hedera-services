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

package com.swirlds.common.threading.utility;

import static com.swirlds.logging.LogMarker.THREADS;

import com.swirlds.common.threading.framework.StoppableThread;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class for performing common actions with threads.
 */
public final class ThreadUtils {

    private static final Logger logger = LogManager.getLogger(ThreadUtils.class);

    // Prevent instantiation of a static utility class
    private ThreadUtils() {}

    /**
     * Stop the provided threads, and block until they have all stopped.
     *
     * @param threadsToStop
     * 		an array of queue threads to stop. Must not be null.
     */
    public static void stopThreads(final StoppableThread... threadsToStop) throws InterruptedException {
        logger.info(THREADS.getMarker(), "{} thread(s) will be terminated", threadsToStop.length);
        for (final StoppableThread thread : threadsToStop) {
            if (thread != null) {
                logger.info(THREADS.getMarker(), "stopping thread {}", thread.getName());
                thread.stop();
            }
        }

        for (final StoppableThread thread : threadsToStop) {
            if (thread != null) {
                logger.info(THREADS.getMarker(), "joining thread {}", thread.getName());
                thread.join();
            }
        }
        logger.info(THREADS.getMarker(), "{} thread(s) terminated", threadsToStop.length);
    }
}
