module com.hedera.storage.blocknode.state {
    // Export the packages that should be available to other modules.
    exports com.hedera.node.blocknode.state;

    // 'requires transitive' - modules which are needed for compilation and should be available to
    // all modules that depend on this  module (including tests and other source sets)
    // 'require' - modules needed only for compilation of this module
    requires transitive com.swirlds.platform.core;
    requires com.hedera.storage.blocknode.core.spi;
}
