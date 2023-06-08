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
import com.swirlds.common.system.platformstatus.PlatformStatusConfig;
import com.swirlds.common.time.Time;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Factory class for creating {@link PlatformStatusLogic} objects
 */
public class StatusLogicFactory {
    /**
     * Hidden constructor
     */
    private StatusLogicFactory() {}

    /**
     * Create a {@link PlatformStatusLogic} object for the given {@link PlatformStatus}
     *
     * @param status the status
     * @param time   the time object
     * @param config the platform status config
     * @return a new {@link PlatformStatusLogic} object
     */
    @NonNull
    public static PlatformStatusLogic createStatusLogic(
            @NonNull final PlatformStatus status,
            @NonNull final Time time,
            @NonNull final PlatformStatusConfig config) {

        return switch (status) {
            case STARTING_UP -> new StartingUpStatusLogic(time, config);
            case ACTIVE -> new ActiveStatusLogic(time, config);
            case DISCONNECTED -> throw new IllegalArgumentException(
                    "The DISCONNECTED status cannot be reached with the PlatformStatus state machine");
            case BEHIND -> new BehindStatusLogic(time, config);
            case FREEZING -> new FreezingStatusLogic(time, config);
            case FREEZE_COMPLETE -> new FreezeCompleteStatusLogic(time, config);
            case REPLAYING_EVENTS -> new ReplayingEventsStatusLogic(time, config);
            case OBSERVING -> new ObservingStatusLogic(time, config);
            case CHECKING -> new CheckingStatusLogic(time, config);
            case RECONNECT_COMPLETE -> new ReconnectCompleteStatusLogic(time, config);
            case CATASTROPHIC_FAILURE -> new CatastrophicFailureStatusLogic(time, config);
            case SAVING_FREEZE_STATE -> new SavingFreezeStateStatusLogic(time, config);
        };
    }
}
