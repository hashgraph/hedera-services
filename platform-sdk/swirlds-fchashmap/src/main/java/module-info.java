/**
 * A HashMap-like structure that implements the FastCopyable interface.
 */
module com.swirlds.fchashmap {
    requires com.swirlds.common;
    requires com.swirlds.logging;
    requires org.apache.logging.log4j;
    requires org.apache.commons.lang3;
    requires com.swirlds.config;
    requires static com.github.spotbugs.annotations;

    exports com.swirlds.fchashmap;
    exports com.swirlds.fchashmap.config;
    exports com.swirlds.fchashmap.internal to
            com.swirlds.fchashmap.test;
}
