package com.swirlds.sample.foo;

import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.Loggers;
import com.swirlds.sample.Markers;

public class FooClass {
    private static final Logger LOGGER = Loggers.getLogger(FooClass.class);

    public FooClass () {
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

        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_2).withMarker(Markers.MARKER_3).error(
                "Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_2).withMarker(Markers.MARKER_3).warn(
                "Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_2).withMarker(Markers.MARKER_3).info(
                "Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_2).withMarker(Markers.MARKER_3).debug(
                "Hello Foo!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_2).withMarker(Markers.MARKER_3).trace(
                "Hello Foo!");

    }

}
