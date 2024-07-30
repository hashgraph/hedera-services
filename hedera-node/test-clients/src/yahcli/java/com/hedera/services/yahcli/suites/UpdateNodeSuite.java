/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.yahcli.suites;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.node.HapiNodeUpdate;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class UpdateNodeSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(UpdateNodeSuite.class);

    private final Map<String, String> specConfig;
    private final String nodeId;
    private final String accountId;
    private final String description;
    private final List<ServiceEndpoint> gossipEndPoints;
    private final List<ServiceEndpoint> serviceEndpoints;
    private final byte[] gossipCaCertificate;
    private final byte[] serviceGrpcCertificateHash;
    private final List<Key> adminKeys;
    private final String novelTarget;
    private final int numBusyRetrie;

    public UpdateNodeSuite(
            final Map<String, String> specConfig,
            final String nodeId,
            final String accountId,
            final String description,
            final List<ServiceEndpoint> gossipEndPoints,
            final List<ServiceEndpoint> serviceEndpoints,
            final byte[] gossipCaCertificate,
            final byte[] serviceGrpcCertificateHash,
            final List<Key> adminKeys,
            final String novelTarget,
            final int numBusyRetries) {
        this.specConfig = specConfig;
        this.nodeId = nodeId;
        this.accountId = accountId;
        this.description = description;
        this.gossipEndPoints = gossipEndPoints;
        this.serviceEndpoints = serviceEndpoints;
        this.gossipCaCertificate = gossipCaCertificate;
        this.serviceGrpcCertificateHash = serviceGrpcCertificateHash;
        this.adminKeys = adminKeys;
        this.novelTarget = novelTarget;
        this.numBusyRetrie = numBusyRetries;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(doUpdate());
    }

    final Stream<DynamicTest> doUpdate() {
        Key newList = Key.newBuilder()
                .setKeyList(KeyList.newBuilder().addAllKeys(keys))
                .build();
        HapiTxnOp<?> update = new HapiNodeUpdate(HapiSuite.DEFAULT_SHARD_REALM + targetAccount)
                .signedBy(HapiSuite.DEFAULT_PAYER)
                .protoKey(newList)
                .blankMemo()
                .entityMemo(memo);

        return HapiSpec.customHapiSpec("DoUpdate")
                .withProperties(specConfig)
                .given()
                .when()
                .then(update);
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
