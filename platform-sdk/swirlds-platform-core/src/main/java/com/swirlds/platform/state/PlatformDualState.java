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

package com.swirlds.platform.state;

import com.swirlds.common.system.DualState;
import com.swirlds.platform.FreezePeriodChecker;

/**
 * Contains any data that is either read or written by the platform and the application,
 * and contains methods available to the platform
 */
public interface PlatformDualState extends DualState, FreezePeriodChecker {

    /**
     * Sets the lastFrozenTime to be current freezeTime
     *
     */
    void setLastFrozenTimeToBeCurrentFreezeTime();
}
