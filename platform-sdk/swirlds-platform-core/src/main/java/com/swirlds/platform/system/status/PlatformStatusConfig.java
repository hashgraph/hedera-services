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

package com.swirlds.platform.system.status;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.time.Duration;

/**
 * Configuration for the platform status state machine
 *
 * @param observingStatusDelay              the amount of wall clock time to wait before transitioning out of the
 *                                          OBSERVING status
 * @param activeStatusDelay                 the amount of wall clock time that the status will remain ACTIVE without
 *                                          seeing any self events reach consensus
 * @param statusStateMachineHeartbeatPeriod the amount of wall clock time between heartbeats sent to the status state
 *                                          machine
 */
@ConfigData("platformStatus")
public record PlatformStatusConfig(
        @ConfigProperty(defaultValue = "10s") Duration observingStatusDelay,
        @ConfigProperty(defaultValue = "10s") Duration activeStatusDelay,
        @ConfigProperty(defaultValue = "100ms") Duration statusStateMachineHeartbeatPeriod) {}
