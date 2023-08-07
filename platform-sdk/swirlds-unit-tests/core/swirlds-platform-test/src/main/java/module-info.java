open module com.swirlds.platform.test {
    requires com.swirlds.test.framework;
    requires com.swirlds.platform.core;
    requires com.swirlds.common;
    requires com.swirlds.config.api;
    requires org.junit.jupiter.api;
    requires org.mockito;
    requires com.swirlds.common.testing;
    requires org.apache.logging.log4j;
    requires java.desktop;
    requires static com.github.spotbugs.annotations;
    requires com.swirlds.config.api.test.fixtures;
    requires com.swirlds.common.test.fixtures;
    requires com.swirlds.platform.test.fixtures;
}
