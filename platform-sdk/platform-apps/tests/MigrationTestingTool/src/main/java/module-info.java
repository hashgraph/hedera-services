module com.swirlds.demo.migration {
    requires com.hedera.node.hapi;
    requires com.swirlds.base;
    requires com.swirlds.common;
    requires com.swirlds.config.api;
    requires com.swirlds.fcqueue;
    requires com.swirlds.logging;
    requires com.swirlds.merkle;
    requires com.swirlds.merkledb;
    requires com.swirlds.metrics.api;
    requires com.swirlds.platform.core;
    requires com.swirlds.platform.core.test.fixtures;
    requires com.swirlds.state.api;
    requires com.swirlds.virtualmap;
    requires com.hedera.pbj.runtime;
    requires java.logging;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
}
