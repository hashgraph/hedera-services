module com.hedera.node.app.hapi.utils {
    exports com.hedera.node.app.hapi.utils.fee;
    exports com.hedera.node.app.hapi.utils.contracts;
    exports com.hedera.node.app.hapi.utils.ethereum;
    exports com.hedera.node.app.hapi.utils.exports.recordstreaming;
    exports com.hedera.node.app.hapi.utils.keys;
    exports com.hedera.node.app.hapi.utils.sysfiles.serdes;
    exports com.hedera.node.app.hapi.utils.sysfiles.domain.throttling;
    exports com.hedera.node.app.hapi.utils;

    requires com.fasterxml.jackson.databind;
    requires com.google.protobuf;
    requires com.swirlds.common;
    requires org.apache.logging.log4j;
    requires static jsr305;
    requires org.bouncycastle.provider;
    requires org.bouncycastle.pkix;
    requires org.hyperledger.besu.secp256k1;
    requires com.google.common;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires headlong;
    requires org.apache.commons.codec;
    requires com.sun.jna;
    requires org.apache.commons.lang3;
    requires net.i2p.crypto.eddsa;
    requires javax.inject;
    requires com.hedera.services.evm;
}
