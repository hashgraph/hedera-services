module com.swirlds.base.sample {
    exports com.swirlds.base.sample.config to
            com.swirlds.config.impl;

    opens com.swirlds.base.sample.domain to
            com.fasterxml.jackson.databind;

    requires com.swirlds.common;
    requires com.swirlds.config.api;
    requires com.swirlds.config.extensions;
    requires com.swirlds.metrics.api;
    requires com.fasterxml.jackson.databind;
    requires com.google.common;
    requires jdk.httpserver;
    requires jdk.management;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
}
