import com.swirlds.platform.base.example.app.config.BaseConfigurationExtension;
import com.swirlds.config.api.ConfigurationExtension;

module com.swirlds.platform.base.example.app {
    exports com.swirlds.platform.base.example.app.config to
            com.swirlds.config.impl;

    opens com.swirlds.platform.base.example.app.domain to
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
    requires static com.google.auto.service;

    provides ConfigurationExtension with
            BaseConfigurationExtension;
}
