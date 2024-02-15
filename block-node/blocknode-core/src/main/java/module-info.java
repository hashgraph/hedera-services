module com.hedera.node.blocknode.core {
    // Selectively export non-public packages to the test module.
    exports com.hedera.node.blocknode.core to
            com.hedera.node.blocknode.core.test;

    // Require the modules needed for compilation.
    requires com.hedera.node.blocknode.filesystem.local;
    requires com.hedera.node.blocknode.filesystem.s3;

    // Require modules which are needed for compilation and should be available to all modules that depend on this
    // module (including tests and other source sets).
    requires transitive com.hedera.node.blocknode.core.spi;
    requires transitive com.hedera.node.blocknode.filesystem.api;
    requires transitive com.hedera.node.blocknode.grpc.api;
    requires transitive com.hedera.node.blocknode.state;
    requires transitive com.hedera.node.hapi;
}
