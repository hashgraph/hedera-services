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
import com.swirlds.logging.LogMarker;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class containing the state machine logic for the {@link PlatformStatus#ACTIVE ACTIVE} status.
 */
public class ActiveStatusLogic extends AbstractStatusLogic {
    private static final Logger logger = LogManager.getLogger(ActiveStatusLogic.class);

    /**
     * The last time an own event was observed reaching consensus
     */
    private Instant lastTimeOwnEventReachedConsensus;

    /**
     * Constructor
     *
     * @param time   a source of time
     * @param config the platform status config
     */
    public ActiveStatusLogic(@NonNull final Time time, @NonNull final PlatformStatusConfig config) {
        super(time, config);

        // an own event had to reach consensus to arrive at the ACTIVE status
        this.lastTimeOwnEventReachedConsensus = time.now();
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public PlatformStatus processStatusAction(@NonNull final PlatformStatusAction action) {
        return switch (action) {
            case OWN_EVENT_REACHED_CONSENSUS -> {
                // record the time an own event reached consensus, resetting the timer that would trigger a transition
                // to CHECKING
                lastTimeOwnEventReachedConsensus = getTime().now();
                yield null;
            }
            case FREEZE_PERIOD_ENTERED -> PlatformStatus.FREEZING;
            case FALLEN_BEHIND -> PlatformStatus.BEHIND;
            case STATE_WRITTEN_TO_DISK -> null;
            case CATASTROPHIC_FAILURE -> PlatformStatus.CATASTROPHIC_FAILURE;
            case TIME_ELAPSED -> {
                if (Duration.between(lastTimeOwnEventReachedConsensus, getTime().now())
                                .compareTo(getConfig().activeStatusDelay())
                        > 0) {
                    // if an own event hasn't been observed reaching consensus in the configured duration, go back to
                    // CHECKING
                    yield PlatformStatus.CHECKING;
                } else {
                    yield null;
                }
            }
            default -> {
                logger.error(LogMarker.EXCEPTION.getMarker(), getUnexpectedStatusActionLog(action));
                yield null;
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PlatformStatus getStatus() {
        return PlatformStatus.ACTIVE;
    }
}
