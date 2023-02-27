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

package com.swirlds.platform.components.transaction.throttle;

/**
 * Is used for checking whether this node should initiate a sync and create an event for that sync
 */
public interface TransThrottleSyncAndCreateRule {
    /**
     * Determines whether this node should or should not initiate a sync and create an event for that sync,
     * or should check subsequent rules
     *
     * @return the sync and create action to take
     */
    TransThrottleSyncAndCreateRuleResponse shouldSyncAndCreate();
}
