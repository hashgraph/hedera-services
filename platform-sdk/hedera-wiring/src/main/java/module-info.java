module com.hedera.wiring {
    exports com.hedera.wiring;
    exports com.hedera.wiring.component;
    exports com.hedera.wiring.counters;
    exports com.hedera.wiring.model;
    exports com.hedera.wiring.schedulers;
    exports com.hedera.wiring.schedulers.builders;
    exports com.hedera.wiring.transformers;
    exports com.hedera.wiring.wires;
    exports com.hedera.wiring.wires.input;
    exports com.hedera.wiring.wires.output;

    requires transitive com.swirlds.base;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.logging;
    requires transitive com.swirlds.metrics.api;
    requires transitive com.swirlds.metrics.impl;
    requires transitive com.fasterxml.jackson.core;
    requires transitive com.fasterxml.jackson.databind;
    requires transitive com.hedera.pbj.runtime;
    requires transitive io.prometheus.simpleclient;
    requires transitive lazysodium.java;
    requires transitive org.apache.logging.log4j;
    requires com.sun.jna;
    requires io.github.classgraph;
    requires io.prometheus.simpleclient.httpserver;
    requires java.desktop;
    requires jdk.httpserver;
    requires jdk.management;
    requires org.apache.logging.log4j.core;
    requires org.bouncycastle.provider;
    requires org.hyperledger.besu.nativelib.secp256k1;
    requires static com.github.spotbugs.annotations;
}
