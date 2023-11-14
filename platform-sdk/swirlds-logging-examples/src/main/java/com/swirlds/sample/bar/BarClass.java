package com.swirlds.sample.bar;

import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.Loggers;
import com.swirlds.sample.Markers;

public class BarClass {
    private static final Logger LOGGER = Loggers.getLogger(BarClass.class);

    public BarClass () {
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

        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_2).withMarker(Markers.MARKER_3).error(
                "Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_2).withMarker(Markers.MARKER_3).warn(
                "Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_2).withMarker(Markers.MARKER_3).info(
                "Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_2).withMarker(Markers.MARKER_3).debug(
                "Hello Bar!");
        LOGGER.withMarker(Markers.MARKER_1).withMarker(Markers.MARKER_2).withMarker(Markers.MARKER_3).trace(
                "Hello Bar!");

    }

}
