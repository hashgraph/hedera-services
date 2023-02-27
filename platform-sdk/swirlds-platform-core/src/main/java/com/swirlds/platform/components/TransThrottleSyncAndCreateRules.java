/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components;

import static com.swirlds.platform.components.TransThrottleSyncAndCreateRuleResponse.PASS;

import java.util.List;

/**
 * This class is used for checking whether should initiate a sync and create an event for that sync or not.
 * It contains a list of {@link TransThrottleSyncAndCreateRule}s, which are checked one by one.
 * Once a rule has a firm answer such as THROTTLE or DONT_THROTTLE, the answer is returned;
 * else we continue checking the
 * next rule.
 */
public class TransThrottleSyncAndCreateRules {

    /**
     * a list of rules based on which we check whether should initiate a sync and create an event for that sync or not
     */
    private final List<TransThrottleSyncAndCreateRule> rules;

    public TransThrottleSyncAndCreateRules(List<TransThrottleSyncAndCreateRule> rules) {
        this.rules = rules;
    }

    /**
     * check whether this node should initiate a sync and create an event for that sync
     *
     * @return whether this node should initiate a sync and create an event for that sync
     */
    public TransThrottleSyncAndCreateRuleResponse shouldSyncAndCreate() {
        for (TransThrottleSyncAndCreateRule rule : rules) {
            TransThrottleSyncAndCreateRuleResponse response = rule.shouldSyncAndCreate();
            // if the response is THROTTLE or DONT_THROTTLE, we should return
            // else we continue checking subsequent rules
            if (response != PASS) {
                return response;
            }
        }
        return PASS;
    }
}
