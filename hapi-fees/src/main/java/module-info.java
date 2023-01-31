module com.hedera.services.hapi.fees {
    exports com.hedera.services.pricing;
    exports com.hedera.services.usage;
    exports com.hedera.services.usage.crypto;
    exports com.hedera.services.usage.crypto.entities;
    exports com.hedera.services.usage.consensus;
    exports com.hedera.services.usage.file;
    exports com.hedera.services.usage.state;
    exports com.hedera.services.usage.schedule;
    exports com.hedera.services.usage.token;
    exports com.hedera.services.usage.token.meta;

    requires com.hedera.services.hapi.utils;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.fasterxml.jackson.databind;
    requires org.apache.logging.log4j;
    requires javax.inject;
    requires com.google.protobuf;
    requires org.apache.commons.lang3;
    requires annotations;
    requires com.google.common;
}
