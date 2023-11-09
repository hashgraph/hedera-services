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

package com.swirlds.platform.event.creation.rules;

import static com.swirlds.platform.event.creation.EventCreationStatus.RATE_LIMITED;

import com.swirlds.platform.event.creation.EventCreationStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Combines multiple {@link EventCreationRule} objects into a single object. Allows event creation if all the contained
 * limiters allow event creation.
 */
public class AggregateEventCreationRules implements EventCreationRule {

    private final EventCreationRule[] rules;
    private EventCreationStatus mostRecentStatus = RATE_LIMITED;

    /**
     * Create a new {@link AggregateEventCreationRules} from the given list of rules.
     *
     * @param rules the rules to combine, if no rules are provided then event creation is always permitted.
     * @return an aggregate rule that permits event creation if and only if all rules permit creation.
     */
    public static AggregateEventCreationRules of(@Nullable final EventCreationRule... rules) {
        return new AggregateEventCreationRules(rules);
    }

    /**
     * Constructor.
     *
     * @param rules the limiters to combine
     */
    private AggregateEventCreationRules(@Nullable final EventCreationRule... rules) {
        this.rules = rules == null ? new EventCreationRule[0] : rules;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventCreationPermitted() {
        for (final EventCreationRule rule : rules) {
            if (!rule.isEventCreationPermitted()) {
                mostRecentStatus = rule.getEventCreationStatus();
                return false;
            }
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventWasCreated() {
        for (final EventCreationRule rule : rules) {
            rule.eventWasCreated();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note: this method only returns the current status if the most recent call to {@link #isEventCreationPermitted()}
     * returned false, otherwise the value returned by this method is undefined.
     */
    @NonNull
    @Override
    public EventCreationStatus getEventCreationStatus() {
        return mostRecentStatus;
    }
}
