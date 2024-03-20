module com.hedera.storage.blocknode.core {
    // Selectively export non-public packages to the test module.
    exports com.hedera.node.blocknode.core to
            com.hedera.storage.blocknode.core.test;
    exports com.hedera.node.blocknode.core.grpc.impl to
            com.hedera.storage.blocknode.core.test;
    exports com.hedera.node.blocknode.core.services to
            com.hedera.storage.blocknode.core.test;

    // Require the modules needed for compilation.
    requires com.hedera.storage.blocknode.config;
    requires com.hedera.node.config;
    requires com.swirlds.config.api;
    requires grpc.netty;
    requires io.grpc;
    requires org.apache.logging.log4j;
    requires transitive org.apache.commons.io;
    requires static com.github.spotbugs.annotations;
}
