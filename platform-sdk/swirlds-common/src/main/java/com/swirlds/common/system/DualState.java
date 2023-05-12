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

package com.swirlds.common.system;

import java.time.Instant;

/**
 * Contains any data that is either read or written by the platform and the application,
 * and contains methods for reading or writing those data.
 * The methods are available to both the platform and the application.
 */
public interface DualState {
    /**
     * Sets the instant after which the platform will enter maintenance status.
     * When consensus timestamp of a signed state is after this instant,
     * the platform will stop creating events and accepting transactions.
     * This is used to safely shut down the platform for maintenance.
     *
     * @param freezeTime
     * 		an Instant in UTC
     */
    void setFreezeTime(Instant freezeTime);

    /**
     * Gets the time when the freeze starts
     *
     * @return the time when the freeze starts
     */
    Instant getFreezeTime();

    /**
     * Gets the last freezeTime based on which the nodes were frozen
     *
     * @return the last freezeTime based on which the nodes were frozen
     */
    Instant getLastFrozenTime();
}
