module com.swirlds.test.framework {
    exports com.swirlds.test.framework;
    exports com.swirlds.test.framework.context;
    exports com.swirlds.test.framework.config;

    requires com.swirlds.common;
    requires com.swirlds.config.api;
    requires io.github.classgraph;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
}
