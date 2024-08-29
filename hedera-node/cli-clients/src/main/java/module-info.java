module com.hedera.node.services.cli {
    exports com.hedera.services.cli.sign;
    exports com.hedera.services.cli.signedstate;
    exports com.hedera.services.cli.utils;

    requires transitive com.hedera.node.hapi;
    requires transitive com.swirlds.cli;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.platform.core;
    requires transitive com.google.protobuf;
    requires transitive info.picocli;
    requires com.hedera.node.app.hapi.utils;
    requires com.swirlds.base;
    requires com.swirlds.config.api;
    requires com.swirlds.config.extensions;
    requires com.swirlds.state.api;
    requires com.google.common;
    requires com.hedera.evm;
    requires com.hedera.pbj.runtime;
    requires io.github.classgraph;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j;
    requires tuweni.bytes;
    requires tuweni.units;
    requires static com.github.spotbugs.annotations;
}
