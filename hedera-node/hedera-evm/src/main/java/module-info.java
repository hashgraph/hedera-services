/** Provides the core interfaces for the Hedera EVM implementation. */
module com.hedera.evm {
    requires transitive com.hedera.node.hapi;
    requires transitive com.github.benmanes.caffeine;
    requires transitive com.google.protobuf;
    requires transitive dagger;
    requires transitive headlong;
    requires transitive javax.inject;
    requires transitive org.apache.commons.lang3;
    requires transitive org.hyperledger.besu.datatypes;
    requires transitive org.hyperledger.besu.evm;
    requires transitive tuweni.bytes;
    requires transitive tuweni.units;
    requires com.swirlds.common;
    requires com.google.common;
    requires com.hedera.node.app.hapi.utils;
    requires org.bouncycastle.provider;
    requires static com.github.spotbugs.annotations;

    exports com.hedera.node.app.service.evm.store.contracts.utils;
    exports com.hedera.node.app.service.evm.contracts.execution;
    exports com.hedera.node.app.service.evm.store.contracts;
    exports com.hedera.node.app.service.evm.store.models;
    exports com.hedera.node.app.service.evm;
    exports com.hedera.node.app.service.evm.accounts;
    exports com.hedera.node.app.service.evm.contracts.operations;
    exports com.hedera.node.app.service.evm.contracts.execution.traceability;
    exports com.hedera.node.app.service.evm.store.contracts.precompile.codec;
    exports com.hedera.node.app.service.evm.store.contracts.precompile.impl;
    exports com.hedera.node.app.service.evm.store.tokens;
    exports com.hedera.node.app.service.evm.store.contracts.precompile;
    exports com.hedera.node.app.service.evm.store.contracts.precompile.proxy;
    exports com.hedera.node.app.service.evm.store;
}
