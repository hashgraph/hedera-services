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

package com.swirlds.platform.util;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.AutoCloseableNonThrowing;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.StackTrace;
import com.swirlds.common.utility.Startable;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.triggers.error.DeadlockTrigger;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.concurrent.locks.StampedLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class watches for deadlocks and logs debug messages if deadlocks are detected.
 */
public class DeadlockSentinel implements Startable, AutoCloseableNonThrowing {

    private static final Logger logger = LogManager.getLogger(DeadlockSentinel.class);
    private static final int STACK_TRACE_MAX_DEPTH = 16;

    private final ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
    private final StoppableThread thread;
    private final DeadlockTrigger deadlockDispatcher;

    /**
     * Create a new deadlock sentinel, but do not start it.
     *
     * @param threadManager
     * 		responsible for managing thread lifecycles
     * @param dispatchBuilder
     * 		builds dispatchers
     * @param period
     * 		the minimum amount of time that must pass between checking for deadlocks
     */
    public DeadlockSentinel(
            final ThreadManager threadManager, final DispatchBuilder dispatchBuilder, final Duration period) {
        thread = threadManager.newStoppableThreadConfiguration()
                .setComponent("platform")
                .setThreadName("deadlock-sentinel")
                .setMinimumPeriod(period)
                .setWork(this::lookForDeadlocks)
                .build();
        deadlockDispatcher = dispatchBuilder.getDispatcher(this, DeadlockTrigger.class)::dispatch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        thread.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        thread.stop();
    }

    /**
     * Look for deadlocks, and log if deadlocks are discovered.
     */
    private void lookForDeadlocks() {
        final long[] deadlockedThreads = mxBean.findDeadlockedThreads();

        final StampedLock stampedLock = new StampedLock();

        if (deadlockedThreads == null || deadlockedThreads.length == 0) {
            // No threads are currently deadlocked.
            return;
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("Deadlocked threads detected:\n");

        for (final long threadId : deadlockedThreads) {
            captureDeadlockedThreadData(sb, threadId);
        }

        logger.error(EXCEPTION.getMarker(), sb);
        deadlockDispatcher.dispatch();
    }

    /**
     * Write information about a deadlocked thread to a string builder.
     */
    private void captureDeadlockedThreadData(final StringBuilder sb, final long threadId) {
        final ThreadInfo blocked = mxBean.getThreadInfo(threadId, STACK_TRACE_MAX_DEPTH);
        final String blockedName = blocked.getThreadName();

        final ThreadInfo blocker = mxBean.getThreadInfo(blocked.getLockOwnerId());
        final String blockingName = blocker.getThreadName();

        final LockInfo lock = blocked.getLockInfo();
        final String lockName = lock.getClassName();
        final int lockId = lock.getIdentityHashCode();

        sb.append("Thread ")
                .append(blockedName)
                .append(" blocked waiting on ")
                .append(blockingName)
                .append(", lock = ")
                .append(lockName)
                .append("(")
                .append(lockId)
                .append(")\n");

        sb.append(new StackTrace(blocked.getStackTrace())).append("\n");
    }
}
