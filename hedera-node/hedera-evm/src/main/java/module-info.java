/** Provides the core interfaces for the Hedera EVM implementation. */
module com.hedera.node.app.service.evm {
    requires org.hyperledger.besu.evm;
    requires org.hyperledger.besu.datatypes;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires tuweni.bytes;
    requires tuweni.units;
    requires javax.inject;
    requires com.swirlds.common;
    requires com.google.common;
    requires com.github.benmanes.caffeine;
    requires com.google.protobuf;
    requires org.bouncycastle.provider;
    requires com.sun.jna;
    requires static com.github.spotbugs.annotations;
    requires org.apache.commons.lang3;
    requires org.hyperledger.besu.secp256k1;
    requires headlong;

    exports com.hedera.node.app.service.evm.store.contracts.utils;
    exports com.hedera.node.app.service.evm.contracts.execution;
    exports com.hedera.node.app.service.evm.store.contracts;
    exports com.hedera.node.app.service.evm.store.models;
    exports com.hedera.node.app.service.evm;
    exports com.hedera.node.app.service.evm.accounts;
    exports com.hedera.node.app.service.evm.contracts.operations;
    exports com.hedera.node.app.service.evm.contracts.execution.traceability;
    exports com.hedera.node.app.service.evm.utils;
    exports com.hedera.node.app.service.evm.store.contracts.precompile.codec;
    exports com.hedera.node.app.service.evm.store.contracts.precompile.impl;
}
