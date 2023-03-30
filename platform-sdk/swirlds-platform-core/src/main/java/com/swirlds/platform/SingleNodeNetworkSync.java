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

package com.swirlds.platform;

import com.swirlds.base.ArgumentUtils;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An {@link InterruptableRunnable} that imitates sync on a single node network
 */
public class SingleNodeNetworkSync implements InterruptableRunnable {
    /**
     * The platform that this sync is for
     */
    private final SwirldsPlatform platform;

    /**
     * The id of the single running node
     */
    private final long selfId;

    /**
     * Constructor
     *
     * @param platform the platform that this sync is for
     * @param selfId   the id of the single running node
     */
    public SingleNodeNetworkSync(@NonNull final SwirldsPlatform platform, final long selfId) {
        ArgumentUtils.throwArgNull(platform, "platform must not be null");

        this.platform = platform;
        this.selfId = selfId;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Checks platform status, and then creates an event which is immediately added to the hashgraph. This is like
     * "syncing" with self, and then creating an event with otherParent being self.
     */
    @Override
    public void run() throws InterruptedException {
        platform.checkPlatformStatus();
        platform.getEventTaskCreator().createEvent(selfId);

        if (platform.getSleepAfterSync() > 0) {
            Thread.sleep(platform.getSleepAfterSync());
        }
    }
}
