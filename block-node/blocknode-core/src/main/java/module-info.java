module com.hedera.storage.blocknode.core {
    // Selectively export non-public packages to the test module.
    exports com.hedera.node.blocknode.core to
            com.hedera.storage.blocknode.core.test;

    // 'requires transitive' - modules which are needed for compilation and should be available to
    // all modules that depend on this  module (including tests and other source sets)
    // 'require' - modules needed only for compilation of this module
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.storage.blocknode.core.spi;
    requires transitive com.hedera.storage.blocknode.filesystem.api;
    requires transitive com.hedera.storage.blocknode.grpc.api;
    requires transitive com.hedera.storage.blocknode.state;
    requires com.hedera.storage.blocknode.filesystem.local;
    requires com.hedera.storage.blocknode.filesystem.s3;
}
