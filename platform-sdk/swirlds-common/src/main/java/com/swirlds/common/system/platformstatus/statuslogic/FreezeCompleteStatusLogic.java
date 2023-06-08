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
import com.swirlds.common.system.platformstatus.PlatformStatusEvent;
import com.swirlds.common.time.Time;
import com.swirlds.logging.LogMarker;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class containing the state machine logic for the {@link PlatformStatus#FREEZE_COMPLETE FREEZE_COMPLETE} status.
 */
public class FreezeCompleteStatusLogic extends AbstractStatusLogic {
    private static final Logger logger = LogManager.getLogger(FreezeCompleteStatusLogic.class);

    /**
     * Constructor
     *
     * @param time   a source of time
     * @param config the platform status config
     */
    public FreezeCompleteStatusLogic(@NonNull Time time, @NonNull PlatformStatusConfig config) {
        super(time, config);
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public PlatformStatus processStatusEvent(@NonNull PlatformStatusEvent event) {
        // nothing should be happening if a freeze is complete
        logger.error(LogMarker.EXCEPTION.getMarker(), getUnexpectedStatusEventLog(event));
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PlatformStatus getStatus() {
        return PlatformStatus.FREEZE_COMPLETE;
    }
}
