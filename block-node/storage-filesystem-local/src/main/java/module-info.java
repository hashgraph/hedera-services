module com.hedera.storage.filesystem.local {
    // Selectively export non-public packages to the test module.
    exports com.hedera.storage.filesystem.local to
            com.hedera.storage.filesystem.local.test,
            com.hedera.storage.core;

    // Require the modules needed for compilation.
    requires com.hedera.storage.core.spi;

    // Require modules which are needed for compilation and
    // should be available to all modules that depend on this
    // module (including tests and other source sets).
    requires transitive com.hedera.storage.filesystem.api;
}
