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

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.noOp;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.services.bdd.spec.transactions.node.HapiNodeUpdate;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class UpdateNodeSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(UpdateNodeSuite.class);

    private final Map<String, String> specConfig;
    private final long nodeId;

    @Nullable
    private final AccountID accountId;

    @Nullable
    private final String feeAccountKeyLoc;

    @Nullable
    private final String adminKeyLoc;

    @Nullable
    private final String newAdminKeyLoc;

    @Nullable
    private final String description;

    @Nullable
    private final List<ServiceEndpoint> gossipEndpoints;

    @Nullable
    private final List<ServiceEndpoint> hapiEndpoints;

    @Nullable
    private final byte[] gossipCaCertificate;

    @Nullable
    private final byte[] hapiCertificateHash;

    public UpdateNodeSuite(
            @NonNull final Map<String, String> specConfig,
            final long nodeId,
            @Nullable final AccountID accountId,
            @Nullable final String feeAccountKeyLoc,
            @Nullable final String adminKeyLoc,
            @Nullable final String newAdminKeyLoc,
            @Nullable final String description,
            @Nullable final List<ServiceEndpoint> gossipEndpoints,
            @Nullable final List<ServiceEndpoint> hapiEndpoints,
            @Nullable final byte[] gossipCaCertificate,
            @Nullable final byte[] hapiCertificateHash) {
        this.specConfig = requireNonNull(specConfig);
        this.nodeId = nodeId;
        this.accountId = accountId;
        this.feeAccountKeyLoc = feeAccountKeyLoc;
        this.adminKeyLoc = adminKeyLoc;
        this.newAdminKeyLoc = newAdminKeyLoc;
        this.description = description;
        this.gossipEndpoints = gossipEndpoints;
        this.hapiEndpoints = hapiEndpoints;
        this.gossipCaCertificate = gossipCaCertificate;
        this.hapiCertificateHash = hapiCertificateHash;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(doUpdate());
    }

    final Stream<DynamicTest> doUpdate() {
        final var adminKey = "adminKey";
        final var newAdminKey = "newAdminKey";
        final var feeAccountKey = "feeAccountKey";
        return customHapiSpec("UpdateNode")
                .withProperties(specConfig)
                .given(
                        feeAccountKeyLoc == null
                                ? noOp()
                                : keyFromFile(feeAccountKey, feeAccountKeyLoc).yahcliLogged(),
                        adminKeyLoc == null
                                ? noOp()
                                : keyFromFile(adminKey, adminKeyLoc).yahcliLogged(),
                        newAdminKeyLoc == null
                                ? noOp()
                                : keyFromFile(newAdminKey, newAdminKeyLoc).yahcliLogged())
                .when()
                .then(updateOp());
    }

    private HapiNodeUpdate updateOp() {
        final var op = nodeUpdate("" + nodeId);
        if (accountId != null) {
            op.accountId("0.0." + accountId.getAccountNum());
        }
        if (newAdminKeyLoc != null) {
            op.adminKey("newAdminKey");
        }
        if (description != null) {
            op.description(description);
        }
        if (gossipEndpoints != null) {
            op.gossipEndpoint(gossipEndpoints);
        }
        if (hapiEndpoints != null) {
            op.serviceEndpoint(hapiEndpoints);
        }
        if (gossipCaCertificate != null) {
            op.gossipCaCertificate(gossipCaCertificate);
        }
        if (hapiCertificateHash != null) {
            op.grpcCertificateHash(hapiCertificateHash);
        }
        return op.signedBy(availableSigners());
    }

    private String[] availableSigners() {
        final List<String> signers = new ArrayList<>();
        signers.add(DEFAULT_PAYER);
        if (adminKeyLoc != null) {
            signers.add("adminKey");
        }
        if (feeAccountKeyLoc != null) {
            signers.add("feeAccountKey");
        }
        if (newAdminKeyLoc != null) {
            signers.add("newAdminKey");
        }
        return signers.toArray(String[]::new);
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
