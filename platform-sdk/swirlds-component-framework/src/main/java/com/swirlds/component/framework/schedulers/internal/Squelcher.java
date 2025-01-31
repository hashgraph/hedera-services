/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.swirlds.component.framework.schedulers.internal;

/**
 * Manages whether or not tasks scheduled by a given task scheduler should be squelched.
 * <p>
 * Squelching is a mechanism that allows a task scheduler to temporarily suppress the execution of tasks. When a
 * scheduler is being squelched, any new tasks that are received are simply discarded. Any previously scheduled tasks
 * are either cleared, or executed as a no-op.
 */
public interface Squelcher {
    /**
     * Start squelching, and continue doing so until {@link #stopSquelching()} is called.
     *
     * @throws UnsupportedOperationException if squelching is not supported by this scheduler
     * @throws IllegalStateException         if scheduler is already squelching
     */
    void startSquelching();

    /**
     * Stop squelching.
     *
     * @throws UnsupportedOperationException if squelching is not supported by this scheduler
     * @throws IllegalStateException         if scheduler is not currently squelching
     */
    void stopSquelching();

    /**
     * Get whether or not tasks created by the relevant scheduler should be squelched.
     * <p>
     * If squelching isn't enabled, then this method will always return false.
     *
     * @return true if tasks should be squelched, false otherwise
     */
    boolean shouldSquelch();
}
