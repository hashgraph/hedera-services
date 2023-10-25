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

package com.swirlds.platform.event.validation;

import static com.swirlds.logging.LogMarker.INVALID_EVENT_ERROR;

import com.swirlds.platform.internal.EventImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A collection of static methods for validating events
 */
public final class StaticValidators {
    private static final Logger logger = LogManager.getLogger(StaticValidators.class);

    private StaticValidators() {}

    /**
     * Determine whether a given event has a valid creation time.
     *
     * @param event the event to be validated
     * @return true iff the creation time of the event is strictly after the creation time of its self-parent
     */
    public static boolean isValidTimeCreated(final EventImpl event) {
        if (event.getSelfParent() != null) {

            final EventImpl selfParent = event.getSelfParent();
            if (selfParent != null && !event.getTimeCreated().isAfter(selfParent.getTimeCreated())) {

                logger.debug(
                        INVALID_EVENT_ERROR.getMarker(),
                        () -> String.format(
                                "Event timeCreated ERROR event %s created:%s, parent created:%s",
                                event.toMediumString(),
                                event.getTimeCreated().toString(),
                                selfParent.getTimeCreated().toString()));
                return false;
            }
        }

        return true;
    }
}
