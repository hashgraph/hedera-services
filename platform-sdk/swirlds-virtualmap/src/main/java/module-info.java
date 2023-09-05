/**
 * A map that implements the FastCopyable interface.
 */
open module com.swirlds.virtualmap {
    exports com.swirlds.virtualmap;
    exports com.swirlds.virtualmap.datasource;
    // Currently, exported only for tests.
    exports com.swirlds.virtualmap.internal.merkle;
    exports com.swirlds.virtualmap.config;

    requires transitive com.swirlds.common;
    requires transitive com.swirlds.config.api;
    requires com.swirlds.base;
    requires com.swirlds.logging;
    requires java.management; // Test dependency
    requires java.sql;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
}
