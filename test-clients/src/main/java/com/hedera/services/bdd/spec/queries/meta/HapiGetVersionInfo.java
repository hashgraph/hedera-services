/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.queries.meta;

import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.NetworkGetVersionInfoQuery;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.hederahashgraph.api.proto.java.Transaction;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class HapiGetVersionInfo extends HapiQueryOp<HapiGetVersionInfo> {
    private static final Logger LOG = LogManager.getLogger(HapiGetVersionInfo.class);

    private boolean assertNoDegenSemvers = false;
    private Optional<SemanticVersion> expectedProto = Optional.empty();
    private Optional<SemanticVersion> expectedServices = Optional.empty();

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.GetVersionInfo;
    }

    @Override
    protected HapiGetVersionInfo self() {
        return this;
    }

    public HapiGetVersionInfo hasProtoSemVer(SemanticVersion sv) {
        expectedProto = Optional.of(sv);
        return this;
    }

    public HapiGetVersionInfo hasServicesSemVer(SemanticVersion sv) {
        expectedServices = Optional.of(sv);
        return this;
    }

    public HapiGetVersionInfo hasNoDegenerateSemvers() {
        assertNoDegenSemvers = true;
        return this;
    }

    @Override
    @SuppressWarnings("java:S5960")
    protected void assertExpectationsGiven(HapiSpec spec) throws Throwable {
        SemanticVersion actualProto = response.getNetworkGetVersionInfo().getHapiProtoVersion();
        SemanticVersion actualServices =
                response.getNetworkGetVersionInfo().getHederaServicesVersion();
        if (expectedProto.isPresent()) {
            Assertions.assertEquals(expectedProto.get(), actualProto, "Wrong HAPI proto version");
        }
        if (expectedServices.isPresent()) {
            Assertions.assertEquals(
                    expectedServices.get(), actualServices, "Wrong Hedera Services version");
        }
        if (assertNoDegenSemvers) {
            var degenSemver = SemanticVersion.getDefaultInstance();
            Assertions.assertNotEquals(degenSemver, actualProto);
            Assertions.assertNotEquals(degenSemver, actualServices);
        }
    }

    @Override
    protected void submitWith(HapiSpec spec, Transaction payment) {
        Query query = getVersionInfoQuery(payment, false);
        response =
                spec.clients().getNetworkSvcStub(targetNodeFor(spec), useTls).getVersionInfo(query);
        var info = response.getNetworkGetVersionInfo();
        if (verboseLoggingOn) {
            LOG.info(
                    "Versions :: HAPI protobufs @ {}, Hedera Services @ {}",
                    () -> asReadable(info.getHapiProtoVersion()),
                    () -> asReadable(info.getHederaServicesVersion()));
        }

        if (yahcliLogger) {
            COMMON_MESSAGES.info(
                    String.format(
                            "Versions :: HAPI protobufs @ %s, Hedera Services @ %s",
                            asReadable(info.getHapiProtoVersion()),
                            asReadable(info.getHederaServicesVersion())));
        }
    }

    private String asReadable(SemanticVersion semver) {
        var sb =
                new StringBuilder()
                        .append(semver.getMajor())
                        .append(".")
                        .append(semver.getMinor())
                        .append(".")
                        .append(semver.getPatch());
        var preRelease = semver.getPre();
        if (!preRelease.isBlank()) {
            sb.append("-").append(preRelease);
        }

        var buildMeta = semver.getBuild();
        if (!buildMeta.isBlank()) {
            sb.append("+").append(buildMeta);
        }

        return sb.toString();
    }

    @Override
    protected long lookupCostWith(HapiSpec spec, Transaction payment) throws Throwable {
        Query query = getVersionInfoQuery(payment, true);
        Response response =
                spec.clients().getNetworkSvcStub(targetNodeFor(spec), useTls).getVersionInfo(query);
        return costFrom(response);
    }

    private Query getVersionInfoQuery(Transaction payment, boolean costOnly) {
        NetworkGetVersionInfoQuery getVersionQuery =
                NetworkGetVersionInfoQuery.newBuilder()
                        .setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
                        .build();
        return Query.newBuilder().setNetworkGetVersionInfo(getVersionQuery).build();
    }

    @Override
    protected boolean needsPayment() {
        return true;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return MoreObjects.toStringHelper(this);
    }
}
