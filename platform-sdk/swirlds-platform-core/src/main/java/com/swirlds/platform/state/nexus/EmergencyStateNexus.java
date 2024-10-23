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

package com.swirlds.platform.state.nexus;

import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A thread-safe container that also manages reservations for the emergency state.
 */
public class EmergencyStateNexus extends LockFreeStateNexus {
    /**
     * Clears the current state when the platform becomes active.
     *
     * @param status the new platform status
     */
    public void platformStatusChanged(@NonNull final PlatformStatus status) {
        if (status == PlatformStatus.ACTIVE) {
            clear();
        }
    }
}
