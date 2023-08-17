/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.linking;

import static com.swirlds.logging.LogMarker.INVALID_EVENT_ERROR;

import com.swirlds.base.time.Time;
import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.logging.LogMarker;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An {@link EventLinker} which expects events to be provided in topological order. If an
 * out-of-order event is provided, it is logged and discarded.
 */
public class InOrderLinker extends AbstractEventLinker {
    private static final Logger logger = LogManager.getLogger(InOrderLinker.class);
    private final ParentFinder parentFinder;
    /** Provides the most recent event by the supplied creator ID */
    private final Function<NodeId, EventImpl> mostRecentEvent;

    private EventImpl linkedEvent = null;

    private final RateLimitedLogger unprocessedEventLogger;
    private final RateLimitedLogger missingParentsLogger;

    public InOrderLinker(
            @NonNull final Time time,
            @NonNull final ConsensusConfig config,
            @NonNull final ParentFinder parentFinder,
            @NonNull final Function<NodeId, EventImpl> mostRecentEvent) {

        super(config);
        this.parentFinder = Objects.requireNonNull(parentFinder, "parentFinder must not be null");
        this.mostRecentEvent = Objects.requireNonNull(mostRecentEvent, "mostRecentEvent must not be null");
        Objects.requireNonNull(time);

        unprocessedEventLogger = new RateLimitedLogger(logger, time, Duration.ofMinutes(1));
        missingParentsLogger = new RateLimitedLogger(logger, time, Duration.ofMinutes(1));
    }

    /** {@inheritDoc} */
    @Override
    public void linkEvent(final GossipEvent event) {
        final ChildEvent childEvent = parentFinder.findParents(event, getMinGenerationNonAncient());
        if (childEvent.isOrphan()) {
            logMissingParents(childEvent);
            childEvent.orphanForever();
            return;
        }
        if (linkedEvent != null) {
            unprocessedEventLogger.error(
                    LogMarker.EXCEPTION.getMarker(),
                    "Unprocessed linked event: {}",
                    EventStrings.toMediumString(linkedEvent));
            linkedEvent.clear();
        }
        linkedEvent = childEvent.getChild();
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasLinkedEvents() {
        return linkedEvent != null;
    }

    /** {@inheritDoc} */
    @Override
    public EventImpl pollLinkedEvent() {
        final EventImpl tmp = linkedEvent;
        linkedEvent = null;
        return tmp;
    }

    private void logMissingParents(final ChildEvent event) {
        final GossipEvent e = event.getChild().getBaseEvent();
        missingParentsLogger.error(
                INVALID_EVENT_ERROR.getMarker(),
                "Invalid event! {} missing for {} min gen:{}\n"
                        + "most recent event by missing self parent creator:{}\n"
                        + "most recent event by missing self parent creator:{}",
                event.missingParentsString(),
                EventStrings.toMediumString(e),
                getMinGenerationNonAncient(),
                EventStrings.toShortString(
                        mostRecentEvent.apply(e.getHashedData().getCreatorId())),
                EventStrings.toShortString(
                        mostRecentEvent.apply(e.getUnhashedData().getOtherId())));
    }
}
