/**
 * A map that implements the FastCopyable interface.
 */
open module com.swirlds.merkle.test.fixtures {
    exports com.swirlds.merkle.test.fixtures;
    exports com.swirlds.merkle.test.fixtures.map.lifecycle;
    exports com.swirlds.merkle.test.fixtures.map.pta;

    requires com.swirlds.merkle;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.common.test.fixtures;
    requires com.swirlds.fchashmap;
    requires com.swirlds.fcqueue;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j;
    requires transitive com.fasterxml.jackson.annotation;
    requires transitive com.fasterxml.jackson.databind;
    requires com.swirlds.base;
    requires com.swirlds.logging;
    requires com.fasterxml.jackson.core;
}
