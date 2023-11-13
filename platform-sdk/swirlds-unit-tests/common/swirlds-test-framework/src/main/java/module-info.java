module com.swirlds.test.framework {
    exports com.swirlds.test.framework;
    exports com.swirlds.test.framework.context;
    exports com.swirlds.test.framework.config;

    requires com.swirlds.base;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.config.api;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
}
