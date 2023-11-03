import com.swirlds.logging.api.extensions.handler.LogHandlerFactory;
import com.swirlds.logging.api.extensions.provider.LogProviderFactory;

module com.swirlds.logging {
    exports com.swirlds.logging.legacy;
    exports com.swirlds.logging.legacy.json;
    exports com.swirlds.logging.legacy.payload;
    exports com.swirlds.logging.api;
    exports com.swirlds.logging.api.extensions.provider;
    exports com.swirlds.logging.api.extensions.handler;
    exports com.swirlds.logging.api.extensions.emergency;
    exports com.swirlds.logging.api.extensions.event;
    exports com.swirlds.logging.api.internal.level to
            com.swirlds.loggingfixture,
            com.swirlds.logging.handler.synced,
            com.swirlds.logging;
    exports com.swirlds.logging.api.internal.emergency to
            com.swirlds.loggingfixture,
            com.swirlds.logging;
    exports com.swirlds.logging.api.internal.format;
    exports com.swirlds.logging.api.internal;
    exports com.swirlds.logging.api.internal.event;

    requires transitive com.fasterxml.jackson.annotation;
    requires transitive com.fasterxml.jackson.databind;
    requires transitive com.swirlds.config.api;
    requires transitive org.apache.logging.log4j;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.swirlds.base;
    requires static com.github.spotbugs.annotations;

    uses LogHandlerFactory;
    uses LogProviderFactory;
}
