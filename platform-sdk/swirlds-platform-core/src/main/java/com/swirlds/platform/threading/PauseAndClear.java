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

package com.swirlds.platform.threading;

import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.utility.Clearable;

/**
 * Pauses a thread while a {@link Clearable} is cleared. This is useful for externally clearing instances that are not
 * thread-safe
 */
public class PauseAndClear implements Clearable {
    private final StoppableThread thread;
    private final Clearable clearable;

    /**
     * @param thread
     * 		the thread to pause
     * @param clearable
     * 		the {@link Clearable} to clear
     */
    public PauseAndClear(final StoppableThread thread, final Clearable clearable) {
        this.thread = thread;
        this.clearable = clearable;
    }

    @Override
    public void clear() {
        thread.pause();
        try {
            clearable.clear();
        } finally {
            thread.resume();
        }
    }
}
