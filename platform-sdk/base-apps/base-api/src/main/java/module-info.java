module com.swirlds.baseapi {
    requires com.swirlds.base;
    requires com.swirlds.common;
    requires com.swirlds.config.api;
    requires com.swirlds.config.extensions;
    requires com.swirlds.metrics.api;
    requires org.apache.logging.log4j.core;
    requires com.google.common;
    requires io.undertow.server;
    requires static com.github.spotbugs.annotations;
}
