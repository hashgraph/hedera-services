import com.swirlds.logging.log4j.factory.BaseLoggingProvider;
import org.apache.logging.log4j.spi.Provider;

module com.swirlds.logging.log4j.appender {
    requires static com.github.spotbugs.annotations;
    requires static com.google.auto.service;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.logging;
    requires transitive org.apache.logging.log4j.core;
    requires transitive org.apache.logging.log4j;

    provides Provider with
            BaseLoggingProvider;

    exports com.swirlds.logging.log4j.factory;
}
