open module com.swirlds.merkle.test {
    exports com.swirlds.merkle.map.test.pta;
    exports com.swirlds.merkle.map.test.lifecycle;

    requires transitive com.swirlds.common.test.fixtures;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.merkle;
    requires transitive com.fasterxml.jackson.annotation;
    requires transitive com.fasterxml.jackson.databind;
    requires com.swirlds.base;
    requires com.swirlds.fchashmap;
    requires com.swirlds.fcqueue;
    requires com.fasterxml.jackson.core;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j;
}
