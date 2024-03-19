module com.hedera.storage.blocknode.core {
    // Selectively export non-public packages to the test module.
    exports com.hedera.node.blocknode.core to
            com.hedera.storage.blocknode.core.test;
    exports com.hedera.node.blocknode.core.grpc to
            com.hedera.storage.blocknode.core.test;
    exports com.hedera.node.blocknode.core.grpc.impl to com.hedera.storage.blocknode.core.test;
    exports com.hedera.node.blocknode.core.services to com.hedera.storage.blocknode.core.test;

    // Require the modules needed for compilation.
    requires com.hedera.storage.blocknode.config;
    requires com.swirlds.config.api;
    requires io.grpc;
    requires com.hedera.storage.blocknode.filesystem.local;
    requires com.hedera.storage.blocknode.filesystem.s3;
    requires grpc.netty;
    requires grpc.stub;
    requires io.netty.transport.classes.epoll;
    requires io.netty.transport;
    requires org.apache.logging.log4j;
    // Require modules which are needed for compilation and should be available to all modules that depend on this
    // module (including tests and other source sets).
    requires transitive com.hedera.storage.blocknode.core.spi;
    requires transitive com.hedera.storage.blocknode.filesystem.api;
    requires transitive com.hedera.storage.blocknode.grpc.api;
    requires transitive com.hedera.storage.blocknode.state;
    requires transitive com.hedera.node.hapi;
    requires static com.github.spotbugs.annotations;
    requires com.hedera.node.config;
}

