/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.threading;

import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.threading.pool.CachedPoolParallelExecutor;
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.common.threading.pool.ParallelExecutor;
import java.util.concurrent.Callable;

/**
 * Executes two tasks simultaneously and replacing a specified task at a specified phase. Only
 * one instance of this class should be used at a time, i.e. either the caller or listener in a sync but not both.
 */
public class ReplaceSyncPhaseParallelExecutor implements ParallelExecutor {
    private static final int NUMBER_OF_PHASES = 3;

    private final ParallelExecutor executor;
    private volatile int phase;

    private final int phaseToReplace;
    private final int taskNumToReplace;
    private Callable<Void> replacementTask;

    public ReplaceSyncPhaseParallelExecutor(
            final ThreadManager threadManager,
            final int phaseToReplace,
            final int taskNumToReplace,
            final Callable<Void> replacementTask) {
        this.phaseToReplace = phaseToReplace;
        this.taskNumToReplace = taskNumToReplace;
        this.replacementTask = replacementTask;

        executor = new CachedPoolParallelExecutor(threadManager, "sync-phase-thread");
        phase = 1;
    }

    private void incPhase() {
        phase = phase % NUMBER_OF_PHASES + 1;
    }

    public int getPhase() {
        return phase;
    }

    /**
     * Executes two tasks in parallel, skipping one of the tasks if it is the phase defined in {@link
     * ReplaceSyncPhaseParallelExecutor#phaseToReplace}.
     *
     * @param task1
     * 		a task to execute in parallel
     * @param task2
     * 		a task to execute in parallel
     */
    @Override
    public <T> T doParallel(final Callable<T> task1, final Callable<Void> task2) throws ParallelExecutionException {
        try {
            if (phase == phaseToReplace) {
                if (taskNumToReplace == 1) {
                    executor.doParallel(replacementTask, task2);
                    return null;
                } else {
                    return executor.doParallel(task1, replacementTask);
                }
            } else {
                return executor.doParallel(task1, task2);
            }
        } finally {
            incPhase();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isImmutable() {
        return executor.isImmutable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        executor.start();
    }
}
