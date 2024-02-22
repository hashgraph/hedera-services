module com.hedera.storage.core {
    // Selectively export non-public packages to the test module.
    exports com.hedera.storage.core;

    // Require the modules needed for compilation.
    requires com.hedera.storage.filesystem.local;
    requires com.hedera.storage.filesystem.s3;

    // Require modules which are needed for compilation and
    // should be available to all modules that depend on this
    // module (including tests and other source sets).
    requires transitive com.hedera.storage.core.spi;
    requires transitive com.hedera.storage.filesystem.api;
    requires transitive com.hedera.storage.grpc.api;
    requires transitive com.hedera.storage.state;
    requires transitive com.hedera.node.hapi;

    requires org.apache.logging.log4j;
    requires grpc.netty;
    requires grpc.stub;
}
