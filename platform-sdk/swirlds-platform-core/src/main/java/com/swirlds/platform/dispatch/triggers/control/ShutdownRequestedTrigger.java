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

package com.swirlds.platform.dispatch.triggers.control;

import com.swirlds.platform.dispatch.types.TriggerTwo;

/**
 * Sends dispatches when a shutdown is requested.
 */
@FunctionalInterface
public interface ShutdownRequestedTrigger extends TriggerTwo<String, Integer> {

    /**
     * Send a dispatch requesting that the system shut down immediately.
     *
     * @param reason
     * 		A human-readable reason why the shutdown is being requested
     * @param exitCode
     * 		the exit code to return
     */
    @Override
    void dispatch(String reason, Integer exitCode);
}
