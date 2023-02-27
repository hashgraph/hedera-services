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

package com.swirlds.common.io.extendable.extensions.internal;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.swirlds.common.io.extendable.extensions.TimeoutStreamExtension;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This class is responsible for monitoring instances of {@link TimeoutStreamExtension}.
 */
public final class StreamTimeoutManager {

    /**
     * The number of times to check per second.
     */
    private static final double RATE = 10.0;

    /**
     * Timeout extensions that are currently being tracked.
     */
    private static final Queue<TimeoutStreamExtension> extensions = new ConcurrentLinkedQueue<>();

    /**
     * True if the thread has started, otherwise false.
     */
    private static boolean started = false;

    // FUTURE WORK: this is production code, should not use ad hoc manager
    /**
     * The thread that monitors the timeout extensions.
     */
    private static final StoppableThread thread = new StoppableThreadConfiguration<>(getStaticThreadManager())
            .setComponent("timeout-extension")
            .setThreadName("manager")
            .setMaximumRate(RATE)
            .setWork(StreamTimeoutManager::doWork)
            .build();

    private StreamTimeoutManager() {}

    /**
     * Runs in a loop, checks if any timeout extensions have exceeded limits.
     */
    private static void doWork() {
        for (final TimeoutStreamExtension extension : extensions) {
            // This method checks if the stream has timed out, and closes the stream if it has timed out.
            // This method returns true if the extension is still open, and false when it has closed.
            // Once the extension has closed we should deregister the extension.
            if (!extension.checkTimeout()) {
                deregister(extension);
            }
        }
    }

    /**
     * Start the thread if it hasn't yet been started.
     */
    private static synchronized void start() {
        if (!started) {
            thread.start();
            started = true;
        }
    }

    /**
     * Register a new extension.
     *
     * @param extension
     * 		the extension to register
     */
    public static void register(final TimeoutStreamExtension extension) {
        start();
        extensions.add(extension);
    }

    /**
     * De-register an extension that has been closed.
     *
     * @param extension
     * 		the extension that was closed
     */
    private static void deregister(final TimeoutStreamExtension extension) {
        final Iterator<TimeoutStreamExtension> iterator = extensions.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() == extension) {
                iterator.remove();
                return;
            }
        }
    }

    /**
     * Utility method. Get the current number of timeout stream extensions that are currently being monitored
     * by the background thread.
     *
     * @return the number of monitored streams
     */
    public static int getMonitoredStreamCount() {
        return extensions.size();
    }
}
