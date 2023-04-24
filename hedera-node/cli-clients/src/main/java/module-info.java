module com.hedera.services.cli {
    exports com.hedera.services.cli;

    requires static com.github.spotbugs.annotations;
    requires com.hedera.node.app.service.mono;
    requires com.swirlds.cli;
    requires com.swirlds.virtualmap;
    requires com.swirlds.platform;
    requires com.swirlds.common;
    requires info.picocli;
}
