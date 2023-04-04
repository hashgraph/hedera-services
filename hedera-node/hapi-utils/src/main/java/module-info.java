module com.hedera.node.app.hapi.utils {
    exports com.hedera.node.app.hapi.utils.fee;
    exports com.hedera.node.app.hapi.utils.contracts;
    exports com.hedera.node.app.hapi.utils.ethereum;
    exports com.hedera.node.app.hapi.utils.exports.recordstreaming;
    exports com.hedera.node.app.hapi.utils.keys;
    exports com.hedera.node.app.hapi.utils.sysfiles.serdes;
    exports com.hedera.node.app.hapi.utils.sysfiles.domain.throttling;
    exports com.hedera.node.app.hapi.utils;
    exports com.hedera.node.app.hapi.utils.throttles;
    exports com.hedera.node.app.hapi.utils.builder;
    exports com.hedera.node.app.hapi.utils.sysfiles.domain;
    exports com.hedera.node.app.hapi.utils.sysfiles;
    exports com.hedera.node.app.hapi.utils.exports;
    exports com.hedera.node.app.hapi.utils.exception;
    exports com.hedera.node.app.hapi.utils.sysfiles.validation;

    requires transitive com.hedera.node.app.service.evm;
    requires com.github.spotbugs.annotations;
    requires com.fasterxml.jackson.databind;
    requires com.google.protobuf;
    requires org.apache.logging.log4j;
    requires org.bouncycastle.provider;
    requires org.bouncycastle.pkix;
    requires com.google.common;
    requires headlong;
    requires org.apache.commons.codec;
    requires com.sun.jna;
    requires org.apache.commons.lang3;
    requires net.i2p.crypto.eddsa;
    requires javax.inject;
}
