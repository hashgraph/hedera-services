module com.hedera.services.cli {
    exports com.hedera.services.cli;
    exports com.hedera.services.cli.sign;

    requires static com.github.spotbugs.annotations;
    requires com.hedera.node.app.service.mono;
    requires com.swirlds.cli;
    requires com.swirlds.virtualmap;
    requires com.swirlds.platform;
    requires com.swirlds.common;
    requires info.picocli;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires org.apache.commons.lang3;
    requires com.hedera.node.app.hapi.utils;
    requires com.google.protobuf;
}
