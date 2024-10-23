/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gossip.sync.protocol;

import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.Set;

/**
 * A utility class for checking if a platform status permits syncing
 */
public class SyncStatusChecker {
    /**
     * The platform statuses that permit syncing. If the platform isn't in one of these statuses, no syncs will be
     * initiated or accepted
     */
    public static final Collection<PlatformStatus> STATUSES_THAT_PERMIT_SYNC = Set.of(
            PlatformStatus.ACTIVE,
            PlatformStatus.FREEZING,
            PlatformStatus.FREEZE_COMPLETE,
            PlatformStatus.OBSERVING,
            PlatformStatus.CHECKING,
            PlatformStatus.RECONNECT_COMPLETE);

    /**
     * Hidden constructor
     */
    private SyncStatusChecker() {}

    /**
     * Determines if the given status permits syncing
     *
     * @param status the status to check
     * @return true if the status permits syncing, false otherwise
     */
    public static boolean doesStatusPermitSync(@NonNull final PlatformStatus status) {
        return STATUSES_THAT_PERMIT_SYNC.contains(status);
    }
}
