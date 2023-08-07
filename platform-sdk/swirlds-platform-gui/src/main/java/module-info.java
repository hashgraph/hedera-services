module com.swirlds.platform.gui {
    exports com.swirlds.gui;
    exports com.swirlds.gui.hashgraph;
    exports com.swirlds.gui.components;
    exports com.swirlds.gui.model;

    requires transitive java.desktop;
    requires com.swirlds.common;
    requires com.swirlds.logging;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
}
