module com.swirlds.test.framework {
    exports com.swirlds.test.framework;

    requires com.swirlds.base;
    requires transitive com.swirlds.common;
    requires com.swirlds.common.test.fixtures;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
}
