/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.expiry;

/**
 * Summarizes the result of processing an {@code 0.0.X} id that might refer to an expiring entity.
 */
public enum ExpiryProcessResult {
    /** Either the id did not refer to an entity; or that entity is not expired. */
    NOTHING_TO_DO,
    /**
     * The id referred to an expiring entity, but its auto-renewal or auto-removal work could not be
     * completed in the current process step.
     *
     * <p><b>IMPORTANT:</b> Right now, the only reason that auto-renewal or auto-removal work will
     * not complete for an entity is {@link ExpiryProcessResult#NO_CAPACITY_LEFT}. But it is quite
     * conceivable we will use this result value in the future.
     */
    STILL_MORE_TO_DO,
    /**
     * The id referred to an expiring entity, and its auto-renewal or auto-removal work is complete.
     */
    DONE,
    /** The expiry throttle bucket is full, and no more work can be done at this time. */
    NO_CAPACITY_LEFT
}
