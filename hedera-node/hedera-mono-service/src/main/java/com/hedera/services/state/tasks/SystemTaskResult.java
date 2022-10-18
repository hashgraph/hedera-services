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
package com.hedera.services.state.tasks;

/**
 * Represents the outcome of performing a system task an {@code 0.0.X} id.
 *
 * <p>The canonical example is expiration-related work (either auto-renewal or auto-removal of an
 * entity).
 */
public enum SystemTaskResult {
    /** Either the id did not refer to an existing entity; or there was no work to for the task. */
    NOTHING_TO_DO,
    /**
     * The id referred to an entity with work to be done for this task, but the work could not be
     * completed in the current context.
     */
    NEEDS_DIFFERENT_CONTEXT,
    /** The id referred to an entity with work to be done for this task, and the work is done. */
    DONE,
    /** The system task throttle bucket is full, and no more work can be done at this time. */
    NO_CAPACITY_LEFT
}
