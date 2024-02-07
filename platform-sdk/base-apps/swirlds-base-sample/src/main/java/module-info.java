module com.swirlds.base.sample {
    requires com.swirlds.base;
    requires com.swirlds.common;
    requires com.swirlds.config.api;
    requires com.swirlds.config.impl;
    requires com.swirlds.config.extensions;
    requires com.swirlds.metrics.api;
    requires org.apache.logging.log4j.core;
    requires com.google.common;
    requires io.undertow;
    requires static com.github.spotbugs.annotations;
    requires jdk.management;
}
