/**
 * A map that implements the FastCopyable interface.
 */
open module com.swirlds.virtualmap {
    exports com.swirlds.virtualmap;
    exports com.swirlds.virtualmap.datasource;
    // Currently, exported only for tests.
    exports com.swirlds.virtualmap.internal.merkle;
    exports com.swirlds.virtualmap.config;

    requires com.swirlds.common;
    requires com.swirlds.logging;
    requires org.apache.logging.log4j;
    requires java.sql;
    requires java.management; // Test dependency
    requires org.apache.commons.lang3;
    requires com.swirlds.config;
    requires static com.github.spotbugs.annotations;
}
