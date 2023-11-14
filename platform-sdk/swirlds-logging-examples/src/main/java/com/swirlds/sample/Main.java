package com.swirlds.sample;

import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.Loggers;
import com.swirlds.sample.bar.FooClass;
import com.swirlds.sample.foo.BarClass;

public class Main {
    private static final Logger LOGGER = Loggers.getLogger(Main.class);

    public static void main (String[] args) {
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

        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_2).withMarker(Markers.MARKER_3).error(
                "Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_2).withMarker(Markers.MARKER_3).warn(
                "Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_2).withMarker(Markers.MARKER_3).info(
                "Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_2).withMarker(Markers.MARKER_3).debug(
                "Hello from Main!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_2).withMarker(Markers.MARKER_3).trace(
                "Hello from Main!");

        new FooClass();
        new BarClass();
    }
}
