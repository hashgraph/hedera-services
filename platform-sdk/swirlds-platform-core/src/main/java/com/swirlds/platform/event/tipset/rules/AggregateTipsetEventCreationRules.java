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

package com.swirlds.platform.event.tipset.rules;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;

/**
 * Combines multiple {@link TipsetEventCreationRule} objects into a single object. Allows event creation if all the
 * contained limiters allow event creation.
 */
public class AggregateTipsetEventCreationRules implements TipsetEventCreationRule {

    private final List<TipsetEventCreationRule> rules;

    /**
     * Create a new {@link AggregateTipsetEventCreationRules} from the given list of rules.
     *
     * @param rules the rules to combine, if no rules are provided then event creation is always permitted.
     * @return an aggregate rule that permits event creation if and only if all rules permit creation.
     */
    public static AggregateTipsetEventCreationRules of(@NonNull final List<TipsetEventCreationRule> rules) {
        return new AggregateTipsetEventCreationRules(rules);
    }

    /**
     * Constructor.
     *
     * @param rules the limiters to combine
     */
    private AggregateTipsetEventCreationRules(@NonNull final List<TipsetEventCreationRule> rules) {
        this.rules = Objects.requireNonNull(rules);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventCreationPermitted() {
        for (final TipsetEventCreationRule limiter : rules) {
            if (!limiter.isEventCreationPermitted()) {
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
        for (final TipsetEventCreationRule limiter : rules) {
            limiter.eventWasCreated();
        }
    }
}
