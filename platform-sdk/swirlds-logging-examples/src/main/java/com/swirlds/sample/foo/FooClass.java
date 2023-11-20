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

package com.swirlds.sample.foo;

import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.Loggers;
import com.swirlds.sample.Markers;

public class FooClass {
    private static final Logger LOGGER = Loggers.getLogger(FooClass.class);

    public FooClass() {
        LOGGER.error("Hello Foo!");
        LOGGER.warn("Hello Foo!");
        LOGGER.info("Hello Foo!");
        LOGGER.debug("Hello Foo!");
        LOGGER.trace("Hello Foo!");

        LOGGER.withMarker(Markers.MARKER_1).error("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_1).warn("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_1).info("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_1).debug("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_1).trace("Hello Foo!");

        LOGGER.withMarker(Markers.MARKER_2).error("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_2).warn("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_2).info("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_2).debug("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_2).trace("Hello Foo!");

        LOGGER.withMarker(Markers.MARKER_3).error("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_3).warn("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_3).info("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_3).debug("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_3).trace("Hello Foo!");

        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_3).error("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_3).warn("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_3).info("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_3).debug("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_3).trace("Hello Foo!");

        LOGGER.withMarker(Markers.MARKER_1)
                .withMarker(Markers.MARKER_2)
                .withMarker(Markers.MARKER_3)
                .error("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_1)
                .withMarker(Markers.MARKER_2)
                .withMarker(Markers.MARKER_3)
                .warn("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_1)
                .withMarker(Markers.MARKER_2)
                .withMarker(Markers.MARKER_3)
                .info("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_1)
                .withMarker(Markers.MARKER_2)
                .withMarker(Markers.MARKER_3)
                .debug("Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_1)
                .withMarker(Markers.MARKER_2)
                .withMarker(Markers.MARKER_3)
                .trace("Hello Foo!");
    }
}
