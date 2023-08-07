open module com.swirlds.merkle.test {
    exports com.swirlds.merkle.map.test.pta;
    exports com.swirlds.merkle.map.test.lifecycle;

    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.swirlds.common.test.fixtures;
    requires com.swirlds.common.testing;
    requires com.swirlds.common;
    requires com.swirlds.fchashmap;
    requires com.swirlds.fcqueue;
    requires com.swirlds.merkle;
    requires com.swirlds.platform.core;
    requires com.swirlds.test.framework;
    requires java.sql;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j;
    requires org.junit.jupiter.api;
}
