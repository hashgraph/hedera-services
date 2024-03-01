import com.swirlds.logging.api.extensions.provider.LogProviderFactory;
import com.swirlds.logging.log4j.appender.Log4JProviderFactory;

module com.swirlds.logging.log4j.appender {
    requires static transitive com.github.spotbugs.annotations;
    requires static transitive com.google.auto.service;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.logging;
    requires transitive org.apache.logging.log4j.core;
    requires transitive org.apache.logging.log4j;

    provides LogProviderFactory with
            Log4JProviderFactory;

    exports com.swirlds.logging.log4j.appender;
}
