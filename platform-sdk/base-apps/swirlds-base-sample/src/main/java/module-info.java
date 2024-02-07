module com.swirlds.base.sample {
    requires com.fasterxml.jackson.databind;
    requires com.google.common;
    requires com.swirlds.base;
    requires com.swirlds.common;
    requires com.swirlds.config.api;
    requires com.swirlds.config.extensions;
    requires com.swirlds.config.impl;
    requires com.swirlds.metrics.api;
    requires io.undertow;
    requires jdk.management;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
}
