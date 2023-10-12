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

package com.swirlds.platform.event.validation;

import com.swirlds.common.system.events.EventDescriptor;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import java.util.List;
import java.util.function.Predicate;

/**
 * A {@link GossipEventValidator} that checks a list of predicates to see if this event is a duplicate
 */
public class EventDeduplication implements GossipEventValidator {

    private final Predicate<EventDescriptor> isDuplicate;
    private final EventIntakeMetrics stats;

    public EventDeduplication(final Predicate<EventDescriptor> isDuplicateCheck, final EventIntakeMetrics stats) {
        this(List.of(isDuplicateCheck), stats);
    }

    public EventDeduplication(
            final List<Predicate<EventDescriptor>> isDuplicateChecks, final EventIntakeMetrics stats) {
        Predicate<EventDescriptor> chain = null;
        for (final Predicate<EventDescriptor> check : isDuplicateChecks) {

            if (chain == null) {
                chain = check;
            } else {
                chain = chain.or(check);
            }
        }
        this.isDuplicate = chain;
        this.stats = stats;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventValid(final GossipEvent event) {
        final boolean duplicate = isDuplicate.test(event.getDescriptor());
        if (duplicate) {
            stats.duplicateEvent();
        } else {
            stats.nonDuplicateEvent();
        }
        return !duplicate;
    }
}
