/**
 * A map that implements the FastCopyable interface.
 */
open module com.swirlds.virtualmap {
    exports com.swirlds.virtualmap;
    exports com.swirlds.virtualmap.datasource;
    // Currently, exported only for tests.
    exports com.swirlds.virtualmap.internal.merkle;
    exports com.swirlds.virtualmap.config;

    // Testing-only exports
    exports com.swirlds.virtualmap.internal to
            com.swirlds.virtualmap.test.fixtures;
    exports com.swirlds.virtualmap.internal.cache to
            com.swirlds.virtualmap.test.fixtures;

    requires transitive com.swirlds.common;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.metrics.api;
    requires com.swirlds.base;
    requires com.swirlds.config.extensions;
    requires com.swirlds.logging;
    requires java.management; // Test dependency
    requires org.apache.logging.log4j;
    requires static transitive com.github.spotbugs.annotations;
}
