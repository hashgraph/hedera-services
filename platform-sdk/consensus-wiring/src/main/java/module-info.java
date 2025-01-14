module com.swirlds.wiring {
    exports com.swirlds.wiring;

    requires transitive com.swirlds.base;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.config.api;
    requires com.swirlds.logging;
    requires com.swirlds.metrics.api;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
}
