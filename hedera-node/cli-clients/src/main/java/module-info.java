module com.hedera.services.cli {
    exports com.hedera.services.cli.sign;
    exports com.hedera.services.cli.signedstate;

    requires static com.github.spotbugs.annotations;
    requires com.google.protobuf;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.hedera.node.app.hapi.utils;
    requires com.hedera.node.app.service.mono;
    requires com.swirlds.cli;
    requires com.swirlds.common;
    requires com.swirlds.platform;
    requires com.swirlds.virtualmap;
    requires info.picocli;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j;
    requires tuweni.bytes;
    requires tuweni.units;
}
