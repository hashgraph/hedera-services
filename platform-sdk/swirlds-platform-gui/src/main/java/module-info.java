module com.swirlds.gui {
    exports com.swirlds.gui.hashgraph;
    exports com.swirlds.gui;

    requires java.desktop;
    requires org.apache.logging.log4j;
    requires com.swirlds.common;
    requires com.swirlds.logging;
    requires static com.github.spotbugs.annotations;
}
