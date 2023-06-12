module com.hedera.services.cli {
    exports com.hedera.services.cli;
    exports com.hedera.services.cli.sign;

    requires transitive com.swirlds.cli;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.platform;
    requires transitive info.picocli;
    requires com.hedera.node.app.hapi.utils;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.node.hapi;
    requires com.github.spotbugs.annotations;
    requires com.google.common;
    requires com.google.protobuf;
    requires com.swirlds.config;
    requires com.swirlds.virtualmap;
    requires org.apache.commons.lang3;
}
