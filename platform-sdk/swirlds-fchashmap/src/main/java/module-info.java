/**
 * A HashMap-like structure that implements the FastCopyable interface.
 */
module com.swirlds.fchashmap {
    requires com.swirlds.base;
    requires com.swirlds.common;
    requires com.swirlds.config.api;
    requires com.swirlds.logging;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;

    exports com.swirlds.fchashmap;
    exports com.swirlds.fchashmap.config;
}
