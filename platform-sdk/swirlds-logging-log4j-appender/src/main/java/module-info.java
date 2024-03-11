import com.swirlds.logging.api.extensions.provider.LogProviderFactory;
import com.swirlds.logging.log4j.appender.SwrildsLogProviderFactory;

module com.swirlds.logging.log4j.appender {
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
    requires static com.google.auto.service;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.logging;
    requires transitive org.apache.logging.log4j.core;

    provides LogProviderFactory with
            SwrildsLogProviderFactory;
}
