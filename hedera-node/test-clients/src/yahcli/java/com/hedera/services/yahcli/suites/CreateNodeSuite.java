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

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromPbj;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.noOp;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class CreateNodeSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CreateNodeSuite.class);

    private final Map<String, String> specConfig;
    private final AccountID accountId;
    private final String description;
    private final String adminKeyLoc;

    @Nullable
    private final String feeAccountKeyLoc;

    private final List<ServiceEndpoint> gossipEndpoints;
    private final List<ServiceEndpoint> serviceEndpoints;
    private final byte[] gossipCaCertificate;
    private final byte[] grpcCertificateHash;

    @Nullable
    private Long createdId;

    public CreateNodeSuite(
            @NonNull final Map<String, String> specConfig,
            @NonNull final AccountID accountId,
            @NonNull final String description,
            @NonNull final List<ServiceEndpoint> gossipEndpoints,
            @NonNull final List<ServiceEndpoint> serviceEndpoints,
            @NonNull final byte[] gossipCaCertificate,
            @NonNull final byte[] grpcCertificateHash,
            @NonNull final String adminKeyLoc,
            @Nullable final String feeAccountKeyLoc) {
        this.specConfig = requireNonNull(specConfig);
        this.accountId = requireNonNull(accountId);
        this.description = requireNonNull(description);
        this.gossipEndpoints = requireNonNull(gossipEndpoints);
        this.serviceEndpoints = requireNonNull(serviceEndpoints);
        this.gossipCaCertificate = requireNonNull(gossipCaCertificate);
        this.grpcCertificateHash = requireNonNull(grpcCertificateHash);
        this.adminKeyLoc = requireNonNull(adminKeyLoc);
        this.feeAccountKeyLoc = feeAccountKeyLoc;
    }

    public long createdIdOrThrow() {
        return requireNonNull(createdId);
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(createNode());
    }

    final Stream<DynamicTest> createNode() {
        final var adminKey = "adminKey";
        final var feeAccountKey = "feeAccountKey";
        return HapiSpec.customHapiSpec("CreateNode")
                .withProperties(specConfig)
                .given(
                        keyFromFile(adminKey, adminKeyLoc).yahcliLogged(),
                        feeAccountKeyLoc == null
                                ? noOp()
                                : keyFromFile(feeAccountKey, feeAccountKeyLoc).yahcliLogged())
                .when()
                .then(nodeCreate("node")
                        .signedBy(availableSigners())
                        .accountId(accountId)
                        .description(description)
                        .gossipEndpoint(fromPbj(gossipEndpoints))
                        .serviceEndpoint(fromPbj(serviceEndpoints))
                        .gossipCaCertificate(gossipCaCertificate)
                        .grpcCertificateHash(grpcCertificateHash)
                        .adminKey(adminKey)
                        .advertisingCreation()
                        .exposingCreatedIdTo(createdId -> this.createdId = createdId));
    }

    private String[] availableSigners() {
        if (feeAccountKeyLoc == null) {
            return new String[] {DEFAULT_PAYER, "adminKey"};
        } else {
            return new String[] {DEFAULT_PAYER, "adminKey", "feeAccountKey"};
        }
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
