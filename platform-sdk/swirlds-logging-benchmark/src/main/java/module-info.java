module com.swirlds.logging.benchmark {
    exports com.swirlds.logging.benchmark;

    requires transitive com.swirlds.logging;
    requires transitive org.apache.logging.log4j;
    requires com.swirlds.config.api;
    requires org.apache.logging.log4j.core;
    requires static com.github.spotbugs.annotations;
    requires static com.google.auto.service;
}
