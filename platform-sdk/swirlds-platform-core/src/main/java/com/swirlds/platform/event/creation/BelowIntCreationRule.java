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

package com.swirlds.platform.event.creation;

import com.swirlds.common.system.EventCreationRule;
import com.swirlds.common.system.EventCreationRuleResponse;
import java.util.function.IntSupplier;

/**
 * Throttles event creation if the int value supplied is higher than the threshold supplied
 */
public class BelowIntCreationRule implements EventCreationRule {
    private final IntSupplier intToCheck;
    private final int threshold;

    /**
     * @param intToCheck
     * 		the value to check against the threshold
     * @param threshold
     * 		the threshold over we should stop event creation
     */
    public BelowIntCreationRule(final IntSupplier intToCheck, final int threshold) {
        this.intToCheck = intToCheck;
        this.threshold = threshold;
    }

    @Override
    public EventCreationRuleResponse shouldCreateEvent() {
        if (intToCheck.getAsInt() > threshold) {
            return EventCreationRuleResponse.DONT_CREATE;
        }
        return EventCreationRuleResponse.PASS;
    }
}
