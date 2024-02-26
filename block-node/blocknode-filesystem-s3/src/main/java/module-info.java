module com.hedera.node.blocknode.filesystem.s3 {
    // Selectively export non-public packages to the test module.
    exports com.hedera.node.blocknode.filesystem.s3 to
            com.hedera.node.blocknode.filesystem.s3.test,
            com.hedera.node.blocknode.core;

    // Require the modules needed for compilation.
    requires com.hedera.node.blocknode.core.spi;

    // Require modules which are needed for compilation and should be available to all modules that depend on this
    // module (including tests and other source sets).
    requires transitive com.hedera.node.blocknode.filesystem.api;
}
