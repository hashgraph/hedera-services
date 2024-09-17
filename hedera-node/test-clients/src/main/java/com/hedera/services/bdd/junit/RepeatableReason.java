/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit;

/**
 * Enumerates reasons a {@link RepeatableHapiTest} is marked as such.
 */
public enum RepeatableReason {
    /**
     * The test takes excessively long to run without virtual time.
     */
    NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION,
    /**
     * The test needs time to "stop" after each transaction is handled because it wants to assert
     * something about how the last-assigned consensus time is used.
     */
    NEEDS_LAST_ASSIGNED_CONSENSUS_TIME,
    /**
     * The test needs the handle workflow to be synchronous.
     */
    NEEDS_SYNCHRONOUS_HANDLE_WORKFLOW,
    /**
     * The test needs to control behavior of the TSS subsystem.
     */
    NEEDS_TSS_CONTROL,
}
