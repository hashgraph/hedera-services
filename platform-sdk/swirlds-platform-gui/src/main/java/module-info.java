module com.swirlds.gui {
    exports com.swirlds.gui;
    exports com.swirlds.gui.hashgraph;
    exports com.swirlds.gui.components;
    exports com.swirlds.gui.model;

    requires transitive java.desktop;
    requires com.swirlds.common;
    requires static com.github.spotbugs.annotations;
    requires com.swirlds.logging;
    requires org.apache.logging.log4j;
}
