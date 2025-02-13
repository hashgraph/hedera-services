/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

module com.hedera.node.app.hapi.utils {
    exports com.hedera.node.app.hapi.utils.fee;
    exports com.hedera.node.app.hapi.utils.forensics;
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
    exports com.hedera.node.app.hapi.utils.sysfiles.validation;

    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive com.swirlds.common;
    requires transitive com.esaulpaugh.headlong;
    requires transitive com.google.protobuf;
    requires transitive dagger;
    requires transitive java.compiler;
    requires transitive javax.inject;
    requires transitive net.i2p.crypto.eddsa;
    requires transitive org.apache.commons.lang3;
    requires transitive org.hyperledger.besu.evm;
    requires transitive org.hyperledger.besu.nativelib.secp256k1;
    requires transitive tuweni.bytes;
    requires com.swirlds.base;
    requires com.fasterxml.jackson.databind;
    requires com.google.common;
    requires com.sun.jna;
    requires org.apache.commons.codec;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j;
    requires org.bouncycastle.pkix;
    requires org.bouncycastle.provider;
    requires static transitive com.github.spotbugs.annotations;
}
