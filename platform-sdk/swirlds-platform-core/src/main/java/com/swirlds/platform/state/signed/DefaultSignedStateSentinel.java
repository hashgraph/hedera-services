/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.utility.CompareTo;
import com.swirlds.common.utility.RuntimeObjectRecord;
import com.swirlds.common.utility.RuntimeObjectRegistry;
import com.swirlds.common.utility.throttle.RateLimiter;
import com.swirlds.platform.config.StateConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This object is responsible for observing the lifespans of signed states, and taking action if a state suspected of a
 * memory leak is observed.
 */
public class DefaultSignedStateSentinel implements SignedStateSentinel {

    private static final Logger logger = LogManager.getLogger(DefaultSignedStateSentinel.class);

    private final Time time;
    private final RateLimiter rateLimiter;

    private final Duration maxSignedStateAge;

    /**
     * Create an object that monitors signed state lifespans.
     *
     * @param platformContext the current platform's context
     */
    public DefaultSignedStateSentinel(@NonNull final PlatformContext platformContext) {
        this.time = platformContext.getTime();
        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        maxSignedStateAge = stateConfig.suspiciousSignedStateAge();
        final Duration rateLimitPeriod = stateConfig.signedStateAgeNotifyRateLimit();
        rateLimiter = new RateLimiter(Time.getCurrent(), rateLimitPeriod);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void checkSignedStates(@NonNull final Instant now) {
        if (!rateLimiter.request()) {
            return;
        }
        final RuntimeObjectRecord objectRecord = RuntimeObjectRegistry.getOldestActiveObjectRecord(SignedState.class);
        if (objectRecord == null) {
            return;
        }

        if (CompareTo.isGreaterThan(objectRecord.getAge(time.now()), maxSignedStateAge)
                && rateLimiter.requestAndTrigger()) {
            final SignedStateHistory history = objectRecord.getMetadata();
            logger.error(
                    EXCEPTION.getMarker(),
                    "Old signed state detected. The most likely causes are either that the node has gotten stuck or that there has been a memory leak.\n{}",
                    history);
        }
    }
}
