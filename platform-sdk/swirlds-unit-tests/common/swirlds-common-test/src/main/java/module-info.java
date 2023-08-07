open module com.swirlds.common.testing {
    exports com.swirlds.common.test.merkle.util;
    exports com.swirlds.common.test.merkle.dummy;
    exports com.swirlds.common.test.dummy;
    exports com.swirlds.common.test.benchmark;
    exports com.swirlds.common.test.set;
    exports com.swirlds.common.test.map;

    requires com.fasterxml.jackson.databind;
    requires com.swirlds.base;
    requires com.swirlds.common.test.fixtures;
    requires com.swirlds.common;
    requires com.swirlds.test.framework;
    requires java.scripting;
    requires lazysodium.java;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;
    requires org.bouncycastle.provider;
    requires org.junit.jupiter.api;
    requires static com.github.spotbugs.annotations;
}
