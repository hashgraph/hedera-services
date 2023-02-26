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

    requires com.swirlds.logging;
    requires com.swirlds.test.framework;
    requires com.swirlds.common.test;
    requires com.swirlds.merkle;
    requires com.swirlds.virtualmap;
    requires com.swirlds.jasperdb;

    exports com.swirlds.demo.platform.fs.stresstest.proto to
            com.google.protobuf,
            com.fasterxml.jackson.databind;

    opens com.swirlds.demo.platform;
    opens com.swirlds.demo.merkle.map;

    exports com.swirlds.demo.virtualmerkle.config to
            com.fasterxml.jackson.databind;

    requires java.logging;
    requires com.swirlds.platform;
    requires com.swirlds.fcqueue;
    requires org.bouncycastle.provider;
    requires com.google.protobuf;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires lazysodium.java;
    requires java.management;
    requires org.apache.commons.lang3;
    requires commons.math3;
    requires org.apache.commons.io;
    requires com.swirlds.merkle.test;
    requires java.sql;
    requires com.swirlds.fchashmap;
}
