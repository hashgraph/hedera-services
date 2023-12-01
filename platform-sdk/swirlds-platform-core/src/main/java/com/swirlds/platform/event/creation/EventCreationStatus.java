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

/**
 * Describes various status that the event creator may be in.
 */
public enum EventCreationStatus {
    /**
     * The event creator is in the process of attempting to create an event. This may or may not be successful.
     */
    ATTEMPTING_CREATION,
    /**
     * Events are not currently being created because there are currently no events to serve as parents.
     */
    NO_ELIGIBLE_PARENTS,
    /**
     * Events are not currently being created because of the event creation rate limit.
     */
    RATE_LIMITED,
    /**
     * Events can't currently be created due to backpressure preventing the most recent event from being submitted to
     * the intake pipeline.
     */
    PIPELINE_INSERTION,
    /**
     * Event creation is not permitted by the current platform status.
     */
    PLATFORM_STATUS,
    /**
     * Event creation is not permitted because this node is currently overloaded and is not keeping up with the required
     * work load.
     */
    OVERLOADED,
    /**
     * Event creation has been paused by a controlling workflow (or it has not yet been started).
     */
    PAUSED
}
