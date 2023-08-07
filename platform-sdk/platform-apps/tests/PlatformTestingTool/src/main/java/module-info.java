module com.swirlds.demo.platform {
    exports com.swirlds.demo.platform;
    exports com.swirlds.demo.virtualmerkle.map.account;
    exports com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode;
    exports com.swirlds.demo.virtualmerkle.map.smartcontracts.data;
    exports com.swirlds.demo.merkle.map;
    exports com.swirlds.demo.platform.freeze;
    exports com.swirlds.demo.platform.iss;
    exports com.swirlds.demo.platform.nft;
    exports com.swirlds.demo.platform.nft.config;
    exports com.swirlds.demo.platform.actions;

    opens com.swirlds.demo.virtualmerkle;
    opens com.swirlds.demo.merkle.map.internal;

    exports com.swirlds.demo.platform.fs.stresstest.proto to
            com.google.protobuf,
            com.fasterxml.jackson.databind;

    opens com.swirlds.demo.platform;
    opens com.swirlds.demo.merkle.map;

    exports com.swirlds.demo.virtualmerkle.config to
            com.fasterxml.jackson.databind;

    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.google.protobuf;
    requires com.swirlds.common.test.fixtures;
    requires com.swirlds.common.testing;
    requires com.swirlds.fchashmap;
    requires com.swirlds.fcqueue;
    requires com.swirlds.jasperdb;
    requires com.swirlds.logging;
    requires com.swirlds.merkle.test;
    requires com.swirlds.merkle;
    requires com.swirlds.platform.core;
    requires com.swirlds.test.framework;
    requires com.swirlds.virtualmap;
    requires java.logging;
    requires java.management;
    requires java.sql;
    requires lazysodium.java;
    requires org.apache.commons.io;
    requires org.apache.commons.math3;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j;
    requires org.bouncycastle.provider;
    requires static com.github.spotbugs.annotations;
}
