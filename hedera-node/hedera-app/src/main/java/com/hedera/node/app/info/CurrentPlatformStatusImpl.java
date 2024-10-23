/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.info;

import com.swirlds.platform.listeners.PlatformStatusChangeListener;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Singleton;

/**
 * An implementation of {@link CurrentPlatformStatus} that uses the {@link Platform} to get the current status.
 */
@Singleton
public class CurrentPlatformStatusImpl implements CurrentPlatformStatus {
    private PlatformStatus status = PlatformStatus.STARTING_UP;

    public CurrentPlatformStatusImpl(@NonNull final Platform platform) {
        platform.getNotificationEngine()
                .register(PlatformStatusChangeListener.class, notification -> status = notification.getNewStatus());
    }

    @NonNull
    @Override
    public PlatformStatus get() {
        return status;
    }
}
