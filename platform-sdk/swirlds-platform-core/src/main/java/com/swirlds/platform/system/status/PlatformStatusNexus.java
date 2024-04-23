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

package com.swirlds.platform.system.status;

import com.swirlds.platform.system.status.actions.PlatformStatusAction;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A nexus for holding the current platform status, and providing thread-safe access to it.
 */
public interface PlatformStatusNexus {
    /**
     * Get the current status
     *
     * @return the current status
     */
    @NonNull
    PlatformStatus getCurrentStatus();

    /**
     * Set a new status
     *
     * @param status the new status
     */
    void setCurrentStatus(@NonNull final PlatformStatus status);

    /**
     * Submit a status action to the state machine
     *
     * @param action the action to submit
     * @return the action, which will be wired into the state machine
     */
    @NonNull
    PlatformStatusAction submitStatusAction(@NonNull final PlatformStatusAction action);
}
