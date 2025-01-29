/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

import com.swirlds.component.framework.component.InputWireLabel;
import com.swirlds.platform.system.status.actions.PlatformStatusAction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

/**
 * A state machine that processes {@link PlatformStatusAction}s
 */
public interface StatusStateMachine {
    /**
     * Submit a status action
     *
     * @param action the action to submit
     * @return the new status after processing the action, or null if the status did not change
     */
    @Nullable
    @InputWireLabel("PlatformStatusAction")
    PlatformStatus submitStatusAction(@NonNull final PlatformStatusAction action);

    /**
     * Inform the state machine that time has elapsed
     *
     * @param time the current time
     * @return the new status after processing the time update, or null if the status did not change
     */
    @Nullable
    @InputWireLabel("evaluate status")
    PlatformStatus heartbeat(@NonNull final Instant time);
}
