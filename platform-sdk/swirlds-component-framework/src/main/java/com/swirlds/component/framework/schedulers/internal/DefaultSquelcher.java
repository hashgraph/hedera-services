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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A squelcher that actually supports squelching.
 */
public class DefaultSquelcher implements Squelcher {
    /**
     * Whether or not tasks should actively be squelched.
     */
    private final AtomicBoolean squelchFlag = new AtomicBoolean(false);

    /**
     * {@inheritDoc}
     */
    @Override
    public void startSquelching() {
        if (!squelchFlag.compareAndSet(false, true)) {
            throw new IllegalStateException("Scheduler is already squelching");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopSquelching() {
        if (!squelchFlag.compareAndSet(true, false)) {
            throw new IllegalStateException("Scheduler is not currently squelching");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldSquelch() {
        return squelchFlag.get();
    }
}
