open module com.swirlds.common.testing {
    exports com.swirlds.common.test.merkle.util;
    exports com.swirlds.common.test.merkle.dummy;

    requires transitive com.swirlds.common;
    requires transitive com.swirlds.config.api;
    requires transitive org.apache.logging.log4j.core;
    requires com.swirlds.base;
    requires com.swirlds.test.framework;
    requires java.scripting;
    requires org.apache.logging.log4j;
    requires org.junit.jupiter.api;
    requires static com.github.spotbugs.annotations;
}
