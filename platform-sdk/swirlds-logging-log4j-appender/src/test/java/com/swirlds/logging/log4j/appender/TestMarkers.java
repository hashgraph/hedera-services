package com.swirlds.logging.log4j.appender;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class TestMarkers {
    public static final Marker grantMarker;
    public static final Marker parentMarker;
    public static final Marker childMarker;

    public static final String GRANT = "GRANT";

    public static final String PARENT = "PARENT";

    public static final String CHILD = "CHILD";

    static {
        grantMarker = MarkerManager.getMarker(GRANT);
        parentMarker = MarkerManager.getMarker(PARENT).addParents(grantMarker);
        childMarker = MarkerManager.getMarker(CHILD).addParents(parentMarker);
    }
}
