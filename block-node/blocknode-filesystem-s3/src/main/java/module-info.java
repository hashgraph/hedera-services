module com.hedera.storage.blocknode.filesystem.s3 {
    // Selectively export non-public packages to the test module.
    exports com.hedera.node.blocknode.filesystem.s3 to
            com.hedera.storage.blocknode.filesystem.s3.test,
            com.hedera.storage.blocknode.core;

    requires com.swirlds.config.api;
    requires software.amazon.awssdk.auth;
    requires software.amazon.awssdk.awscore;
    requires software.amazon.awssdk.core;
    requires software.amazon.awssdk.http.urlconnection;
    requires software.amazon.awssdk.http;
    requires software.amazon.awssdk.regions;
    requires software.amazon.awssdk.utils;
    requires transitive com.hedera.storage.blocknode.config;
    requires transitive com.hedera.storage.blocknode.filesystem.api;
    requires transitive com.hedera.node.hapi;
    requires transitive software.amazon.awssdk.services.s3;
}
