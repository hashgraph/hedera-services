open module com.swirlds.merkle.test.fixtures {
    exports com.swirlds.merkle.test.fixtures.lifecycle;
    exports com.swirlds.merkle.test.fixtures.pta;

    requires com.fasterxml.jackson.core;
    requires com.swirlds.base;
    requires com.swirlds.fchashmap;
    requires com.swirlds.fcqueue;
    requires com.swirlds.logging;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j;
    requires transitive com.fasterxml.jackson.annotation;
    requires transitive com.fasterxml.jackson.databind;
    requires transitive com.swirlds.common.test.fixtures;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.merkle;
}
