module com.swirlds.wiring {
    exports com.swirlds.wiring;
    exports com.swirlds.wiring.component;
    exports com.swirlds.wiring.counters;
    exports com.swirlds.wiring.model;
    exports com.swirlds.wiring.model.diagram;
    exports com.swirlds.wiring.schedulers;
    exports com.swirlds.wiring.schedulers.builders;
    exports com.swirlds.wiring.transformers;
    exports com.swirlds.wiring.wires;
    exports com.swirlds.wiring.wires.input;
    exports com.swirlds.wiring.wires.output;

    requires transitive com.swirlds.base;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.config.api;
    requires com.swirlds.logging;
    requires com.swirlds.metrics.api;
    requires java.desktop;
    requires jdk.httpserver;
    requires jdk.management;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
}
