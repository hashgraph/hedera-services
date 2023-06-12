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

import com.swirlds.common.system.platformstatus.PlatformStatusConfig;
import com.swirlds.common.time.Time;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Objects;

/**
 * Abstract class that implements common logic for {@link PlatformStatusLogic} implementations.
 */
public abstract class AbstractStatusLogic implements PlatformStatusLogic {
    /**
     * Source of time
     */
    private final Time time;

    /**
     * The time at which the status started.
     */
    private final Instant statusStartTime;

    /**
     * The configuration object containing values relevant to the
     * {@link com.swirlds.common.system.platformstatus.PlatformStatusStateMachine PlatformStatusStateMachine}
     */
    private final PlatformStatusConfig config;

    /**
     * Constructor
     *
     * @param time   source of time
     * @param config the configuration object
     */
    protected AbstractStatusLogic(@NonNull final Time time, @NonNull final PlatformStatusConfig config) {
        this.time = Objects.requireNonNull(time);
        this.statusStartTime = time.now();
        this.config = Objects.requireNonNull(config);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Instant getStatusStartTime() {
        return statusStartTime;
    }

    /**
     * Get the source of time
     *
     * @return the source of time
     */
    @NonNull
    protected Time getTime() {
        return time;
    }

    /**
     * Get the configuration object
     *
     * @return the configuration object
     */
    @NonNull
    protected PlatformStatusConfig getConfig() {
        return config;
    }
}
