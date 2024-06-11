module com.hedera.storage.blocknode.filesystem.local {
    // Selectively export non-public packages to the test module.
    exports com.hedera.node.blocknode.filesystem.local to
            com.hedera.storage.blocknode.filesystem.local.test,
            com.hedera.storage.blocknode.core;

    // 'requires transitive' - modules which are needed for compilation and should be available to
    // all modules that depend on this  module (including tests and other source sets)
    // 'require' - modules needed only for compilation of this module
    requires transitive com.hedera.storage.blocknode.filesystem.api;
    requires com.hedera.storage.blocknode.core.spi;
}
