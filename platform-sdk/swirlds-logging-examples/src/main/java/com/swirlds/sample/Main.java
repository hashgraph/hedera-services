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

package com.swirlds.sample;

import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.Loggers;
import com.swirlds.sample.bar.BarClass;
import com.swirlds.sample.foo.FooClass;

public class Main {
    private static final Logger LOGGER = Loggers.getLogger(Main.class);

    public static void main(String[] args) {
        LOGGER.error("Hello from Main!");
        LOGGER.warn("Hello from Main!");
        LOGGER.info("Hello from Main!");
        LOGGER.debug("Hello from Main!");
        LOGGER.trace("Hello from Main!");

        LOGGER.withMarker(Markers.MARKER_1).error("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_1).warn("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_1).info("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_1).debug("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_1).trace("Hello from Main!");

        LOGGER.withMarker(Markers.MARKER_2).error("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_2).warn("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_2).info("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_2).debug("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_2).trace("Hello from Main!");

        LOGGER.withMarker(Markers.MARKER_3).error("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_3).warn("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_3).info("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_3).debug("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_3).trace("Hello from Main!");

        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_3).error("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_3).warn("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_3).info("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_3).debug("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_3).trace("Hello from Main!");

        LOGGER.withMarker(Markers.MARKER_1)
                .withMarker(Markers.MARKER_2)
                .withMarker(Markers.MARKER_3)
                .error("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_1)
                .withMarker(Markers.MARKER_2)
                .withMarker(Markers.MARKER_3)
                .warn("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_1)
                .withMarker(Markers.MARKER_2)
                .withMarker(Markers.MARKER_3)
                .info("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_1)
                .withMarker(Markers.MARKER_2)
                .withMarker(Markers.MARKER_3)
                .debug("Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_1)
                .withMarker(Markers.MARKER_2)
                .withMarker(Markers.MARKER_3)
                .trace("Hello from Main!");

        new FooClass();
        new BarClass();
    }
}
