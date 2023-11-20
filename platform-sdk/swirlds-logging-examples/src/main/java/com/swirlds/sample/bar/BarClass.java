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

package com.swirlds.sample.bar;

import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.Loggers;
import com.swirlds.sample.Markers;

public class BarClass {
    private static final Logger LOGGER = Loggers.getLogger(BarClass.class);

    public BarClass() {
        LOGGER.error("Hello Bar!");
        LOGGER.warn("Hello Bar!");
        LOGGER.info("Hello Bar!");
        LOGGER.debug("Hello Bar!");
        LOGGER.trace("Hello Bar!");

        LOGGER.withMarker(Markers.MARKER_1).error("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_1).warn("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_1).info("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_1).debug("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_1).trace("Hello Bar!");

        LOGGER.withMarker(Markers.MARKER_2).error("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_2).warn("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_2).info("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_2).debug("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_2).trace("Hello Bar!");

        LOGGER.withMarker(Markers.MARKER_3).error("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_3).warn("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_3).info("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_3).debug("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_3).trace("Hello Bar!");

        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_3).error("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_3).warn("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_3).info("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_3).debug("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_3).trace("Hello Bar!");

        LOGGER.withMarker(Markers.MARKER_1)
                .withMarker(Markers.MARKER_2)
                .withMarker(Markers.MARKER_3)
                .error("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_1)
                .withMarker(Markers.MARKER_2)
                .withMarker(Markers.MARKER_3)
                .warn("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_1)
                .withMarker(Markers.MARKER_2)
                .withMarker(Markers.MARKER_3)
                .info("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_1)
                .withMarker(Markers.MARKER_2)
                .withMarker(Markers.MARKER_3)
                .debug("Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_1)
                .withMarker(Markers.MARKER_2)
                .withMarker(Markers.MARKER_3)
                .trace("Hello Bar!");
    }
}
