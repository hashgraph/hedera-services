open module com.swirlds.merkle.test {
    exports com.swirlds.merkle.map.test.pta;
    exports com.swirlds.merkle.map.test.lifecycle;

    requires com.swirlds.common.test;
    requires com.swirlds.test.framework;
    requires com.swirlds.merkle;
    requires com.swirlds.common;
    requires com.swirlds.fcqueue;
    requires com.swirlds.platform;
    requires com.swirlds.fchashmap;
    requires org.junit.jupiter.api;
    requires org.apache.logging.log4j;
    requires org.apache.commons.lang3;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;
    requires java.sql;
}
