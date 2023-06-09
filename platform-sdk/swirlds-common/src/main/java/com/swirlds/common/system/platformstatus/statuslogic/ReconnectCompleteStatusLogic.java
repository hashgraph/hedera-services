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

package com.swirlds.common.system.platformstatus.statuslogic;

import com.swirlds.common.system.platformstatus.PlatformStatus;
import com.swirlds.common.system.platformstatus.PlatformStatusAction;
import com.swirlds.common.system.platformstatus.PlatformStatusConfig;
import com.swirlds.common.time.Time;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Class containing the state machine logic for the {@link PlatformStatus#RECONNECT_COMPLETE RECONNECT_COMPLETE}
 * status.
 */
public class ReconnectCompleteStatusLogic implements PlatformStatusLogic {

    /**
     * Whether a freeze period has been entered
     */
    private boolean freezePeriodEntered = false;

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PlatformStatus processStatusAction(
            @NonNull final PlatformStatusAction action,
            @NonNull final Instant statusStartTime,
            @NonNull final Time time,
            @NonNull final PlatformStatusConfig config) {

        return switch (action) {
            case FREEZE_PERIOD_ENTERED -> {
                freezePeriodEntered = true;
                yield PlatformStatus.RECONNECT_COMPLETE;
            }
            case STATE_WRITTEN_TO_DISK -> {
                // always transition to a new status once a state has been written to disk
                if (freezePeriodEntered) {
                    yield PlatformStatus.FREEZING;
                } else {
                    yield PlatformStatus.CHECKING;
                }
            }
            case CATASTROPHIC_FAILURE -> PlatformStatus.CATASTROPHIC_FAILURE;
            case TIME_ELAPSED -> PlatformStatus.RECONNECT_COMPLETE;
            default -> throw new IllegalArgumentException(
                    "Unexpected action `%s` while in status `RECONNECT_COMPLETE`".formatted(action));
        };
    }
}
