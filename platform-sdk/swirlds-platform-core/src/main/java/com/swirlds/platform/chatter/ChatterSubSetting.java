/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.chatter;

import com.swirlds.platform.internal.SubSetting;
import java.time.Duration;

/**
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future. If you need
 * 		to use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public class ChatterSubSetting extends SubSetting implements ChatterSettings {
    /** @see #isChatterUsed() */
    public boolean useChatter = false;
    /** @see #getAttemptedChatterEventPerSecond() */
    public int attemptedChatterEventPerSecond = 40;
    /** @see #getChatteringCreationThreshold() */
    public double chatteringCreationThreshold = 0.5;
    /** @see #getChatterIntakeThrottle() */
    public int chatterIntakeThrottle = 20;
    /** @see #getOtherEventDelay() */
    public Duration otherEventDelay = Duration.ofSeconds(2);
    /** @see #getSelfEventQueueCapacity() */
    public int selfEventQueueCapacity = 1500;
    /** @see #getOtherEventQueueCapacity() */
    public int otherEventQueueCapacity = 45000;
    /** @see #getDescriptorQueueCapacity() */
    public int descriptorQueueCapacity = 45000;
    /** @see #getProcessingTimeInterval() */
    public Duration processingTimeInterval = Duration.ofMillis(100);
    /** @see #getHeartbeatInterval() */
    public Duration heartbeatInterval = Duration.ofSeconds(1);
    /** @see #getFutureGenerationLimit() */
    public int futureGenerationLimit = 100_000;
    /** @see #getCriticalQuorumSoftening() */
    public int criticalQuorumSoftening = 50;

    @Override
    public boolean isChatterUsed() {
        return useChatter;
    }

    @Override
    public int getAttemptedChatterEventPerSecond() {
        return attemptedChatterEventPerSecond;
    }

    @Override
    public double getChatteringCreationThreshold() {
        return chatteringCreationThreshold;
    }

    @Override
    public int getChatterIntakeThrottle() {
        return chatterIntakeThrottle;
    }

    @Override
    public Duration getOtherEventDelay() {
        return otherEventDelay;
    }

    @Override
    public int getSelfEventQueueCapacity() {
        return selfEventQueueCapacity;
    }

    @Override
    public int getOtherEventQueueCapacity() {
        return otherEventQueueCapacity;
    }

    @Override
    public int getDescriptorQueueCapacity() {
        return descriptorQueueCapacity;
    }

    @Override
    public Duration getProcessingTimeInterval() {
        return processingTimeInterval;
    }

    @Override
    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    @Override
    public int getFutureGenerationLimit() {
        return futureGenerationLimit;
    }

    @Override
    public int getCriticalQuorumSoftening() {
        return criticalQuorumSoftening;
    }
}
