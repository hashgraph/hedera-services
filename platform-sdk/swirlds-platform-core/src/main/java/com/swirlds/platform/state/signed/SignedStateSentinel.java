/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.signed;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.time.OSTime;
import com.swirlds.common.time.Time;
import com.swirlds.common.utility.CompareTo;
import com.swirlds.common.utility.RuntimeObjectRecord;
import com.swirlds.common.utility.RuntimeObjectRegistry;
import com.swirlds.common.utility.throttle.RateLimiter;
import com.swirlds.platform.Settings;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This object is responsible for observing the lifespans of signed states, and taking action
 * if a state suspected of a memory leak is observed.
 */
public class SignedStateSentinel implements Startable, Stoppable {

    private static final Logger logger = LogManager.getLogger(SignedStateSentinel.class);

    private final Time time;
    private final StoppableThread thread;
    private final RateLimiter rateLimiter;

    private final Duration maxSignedStateAge = Settings.getInstance().getState().suspiciousSignedStateAge;

    /**
     * Create an object that monitors signed state lifespans.
     *
     * @param threadManager
     * 		responsible for creating and managing threads
     * @param time
     * 		provides the wall clock time
     */
    public SignedStateSentinel(final ThreadManager threadManager, final Time time) {
        this.time = time;
        thread = new StoppableThreadConfiguration<>(threadManager)
                .setComponent("platform")
                .setThreadName("signed-state-sentinel")
                .setMinimumPeriod(Duration.ofSeconds(10))
                .setWork(this::checkSignedStates)
                .build();

        rateLimiter = new RateLimiter(OSTime.getInstance(), Duration.ofMinutes(10));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        thread.start();
    }

    @Override
    public void stop() {
        thread.stop();
    }

    /**
     * Check the maximum age of signed states, and take action if a really old state is observed.
     */
    private void checkSignedStates() {
        final RuntimeObjectRecord objectRecord = RuntimeObjectRegistry.getOldestActiveObjectRecord(SignedState.class);
        if (objectRecord == null) {
            return;
        }

        if (CompareTo.isGreaterThan(objectRecord.getAge(time.now()), maxSignedStateAge) && rateLimiter.request()) {
            final SignedStateHistory history = objectRecord.getMetadata();
            logger.error(EXCEPTION.getMarker(), "old signed state detected, memory leak probable.\n{}", history);
        }
    }
}
