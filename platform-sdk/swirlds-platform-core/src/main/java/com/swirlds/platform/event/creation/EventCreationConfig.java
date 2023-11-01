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

package com.swirlds.platform.event.creation;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;

/**
 * Configuration for event creation.
 *
 * @param maxCreationRate                the maximum rate (in hz) that a node can create new events. The maximum rate
 *                                       for the entire network is equal to this value times the number of nodes. A
 *                                       value of 0 means that there is no limit to the number of events that can be
 *                                       created (as long as those events are legal to create).
 * @param antiSelfishnessFactor          the lower this number, the more likely it is that a new event will be created
 *                                       that reduces this node's selfishness score. Setting this too low may result in
 *                                       a suboptimal hashgraph topology. Setting this number too high may lead to some
 *                                       nodes being ignored by selfish nodes and unable to cause their events to reach
 *                                       consensus.
 * @param tipsetSnapshotHistorySize      the number of tipsets to keep in the snapshot history. These tipsets are used
 *                                       to compute selfishness scores.
 * @param eventIntakeThrottle            when the size of the event intake queue equals or exceeds this value, do not
 *                                       permit the creation of new self events.
 * @param creationQueueSize              the size of the intake queue for the event creator
 * @param creationQueueBufferSize        the size of the buffer for the event creator
 * @param creationQueueWaitForWorkPeriod the amount of time the event creator spends waiting for work in its intake
 *                                       queue
 */
@ConfigData("event.creation")
public record EventCreationConfig(
        @ConfigProperty(defaultValue = "20") double maxCreationRate,
        @ConfigProperty(defaultValue = "10") double antiSelfishnessFactor,
        @ConfigProperty(defaultValue = "10") int tipsetSnapshotHistorySize,
        @ConfigProperty(defaultValue = "1024") int eventIntakeThrottle,
        @ConfigProperty(defaultValue = "1024") int creationQueueSize,
        @ConfigProperty(defaultValue = "1024") int creationQueueBufferSize,
        @ConfigProperty(defaultValue = "1 ms") @NonNull Duration creationQueueWaitForWorkPeriod) {}
