/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.queries.contract;

import static com.hedera.services.bdd.spec.HapiApiSpec.ensureDir;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.rethrowSummaryError;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.assertExpectedRels;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.assertNoUnexpectedRels;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asContractId;
import static com.hederahashgraph.api.proto.java.ContractGetInfoResponse.ContractInfo;

import com.google.common.base.MoreObjects;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.assertions.ErroringAsserts;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel;
import com.hederahashgraph.api.proto.java.ContractGetInfoQuery;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.common.utility.CommonUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class HapiGetContractInfo extends HapiQueryOp<HapiGetContractInfo> {
    private static final Logger LOG = LogManager.getLogger(HapiGetContractInfo.class);

    private final String contract;
    private boolean getPredefinedId = false;
    private Optional<String> contractInfoPath = Optional.empty();
    private Optional<String> validateDirPath = Optional.empty();
    private Optional<String> registryEntry = Optional.empty();
    private List<String> absentRelationships = new ArrayList<>();
    private List<ExpectedTokenRel> relationships = new ArrayList<>();
    private Optional<ContractInfoAsserts> expectations = Optional.empty();
    private Optional<Consumer<String>> exposingEvmAddress = Optional.empty();

    public HapiGetContractInfo(String contract) {
        this.contract = contract;
    }

    public HapiGetContractInfo(String contract, boolean idPredefined) {
        this.contract = contract;
        getPredefinedId = idPredefined;
    }

    public HapiGetContractInfo has(ContractInfoAsserts provider) {
        expectations = Optional.of(provider);
        return this;
    }

    public HapiGetContractInfo hasExpectedInfo() {
        expectations = Optional.of(ContractInfoAsserts.contractWith().knownInfoFor(contract));
        return this;
    }

    public HapiGetContractInfo savingTo(String dirPath) {
        contractInfoPath = Optional.of(dirPath);
        return this;
    }

    public HapiGetContractInfo saveToRegistry(String registryEntry) {
        this.registryEntry = Optional.of(registryEntry);
        return this;
    }

    public HapiGetContractInfo checkingAgainst(String dirPath) {
        validateDirPath = Optional.of(dirPath);
        return this;
    }

    public HapiGetContractInfo hasToken(ExpectedTokenRel relationship) {
        relationships.add(relationship);
        return this;
    }

    public HapiGetContractInfo hasNoTokenRelationship(String token) {
        absentRelationships.add(token);
        return this;
    }

    public HapiGetContractInfo exposingEvmAddress(Consumer<String> obs) {
        exposingEvmAddress = Optional.of(obs);
        return this;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.ContractGetInfo;
    }

    @Override
    protected HapiGetContractInfo self() {
        return this;
    }

    @Override
    @SuppressWarnings("java:S5960")
    protected void assertExpectationsGiven(HapiApiSpec spec) throws Throwable {
        ContractInfo actualInfo = response.getContractGetInfo().getContractInfo();
        if (expectations.isPresent()) {
            ErroringAsserts<ContractInfo> asserts = expectations.get().assertsFor(spec);
            List<Throwable> errors = asserts.errorsIn(actualInfo);
            rethrowSummaryError(LOG, "Bad contract info!", errors);
        }
        var actualTokenRels = actualInfo.getTokenRelationshipsList();
        assertExpectedRels(contract, relationships, actualTokenRels, spec);
        assertNoUnexpectedRels(contract, absentRelationships, actualTokenRels, spec);
        expectedLedgerId.ifPresent(
                id -> Assertions.assertEquals(rationalize(id), actualInfo.getLedgerId()));
    }

    @Override
    protected void submitWith(HapiApiSpec spec, Transaction payment) throws Throwable {
        Query query = getContractInfoQuery(spec, payment, false);
        response = spec.clients().getScSvcStub(targetNodeFor(spec), useTls).getContractInfo(query);
        ContractInfo contractInfo = response.getContractGetInfo().getContractInfo();
        if (verboseLoggingOn) {
            LOG.info("Info: {}", contractInfo);
        }
        if (contractInfoPath.isPresent()) {
            saveContractInfo(spec, contractInfo);
        }
        if (validateDirPath.isPresent()) {
            validateAgainst(spec, contractInfo);
        }
        if (registryEntry.isPresent()) {
            spec.registry().saveContractInfo(registryEntry.get(), contractInfo);
        }
        exposingEvmAddress.ifPresent(
                stringConsumer -> stringConsumer.accept(contractInfo.getContractAccountID()));
    }

    @Override
    protected long lookupCostWith(HapiApiSpec spec, Transaction payment) throws Throwable {
        Query query = getContractInfoQuery(spec, payment, true);
        Response response =
                spec.clients().getScSvcStub(targetNodeFor(spec), useTls).getContractInfo(query);
        return costFrom(response);
    }

    private String specScopedDir(HapiApiSpec spec, Optional<String> prefix) {
        return prefix.map(d -> d + "/" + spec.getName()).orElseThrow();
    }

    private void saveContractInfo(HapiApiSpec spec, ContractInfo contractInfo) {
        String specSnapshotDir = specScopedDir(spec, contractInfoPath);
        ensureDir(specSnapshotDir);
        String snapshotDir = specSnapshotDir + "/" + contract;
        ensureDir(snapshotDir);

        try {
            File contractIdFile = new File(snapshotDir + "/contractId.txt");
            ByteSink byteSinkId = Files.asByteSink(contractIdFile);
            byteSinkId.write(contractInfo.getContractID().toByteArray());

            File contractInfoFile = new File(snapshotDir + "/contractInfo.bin");
            ByteSink byteSinkInfo = Files.asByteSink(contractInfoFile);
            byteSinkInfo.write(contractInfo.toByteArray());

            if (verboseLoggingOn) {
                LOG.info(
                        "Saved contractInfo of {} to {}",
                        contractInfo.getContractID(),
                        snapshotDir);
            }
        } catch (Exception e) {
            LOG.error("Couldn't save contractInfo of {}", contractInfo.getContractID(), e);
        }
    }

    private void validateAgainst(HapiApiSpec spec, ContractInfo contractInfo) {
        String specExpectationsDir = specScopedDir(spec, validateDirPath);
        try {
            String expectationsDir = specExpectationsDir + "/" + contract;

            File contractInfoFile = new File(expectationsDir + "/contractInfo.bin");
            ByteSource byteSourceInfo = Files.asByteSource(contractInfoFile);
            ContractInfo savedContractInfo = ContractInfo.parseFrom(byteSourceInfo.read());
            if (verboseLoggingOn) {
                LOG.info("Info: {}", contractInfo);
            }
            Assertions.assertEquals(
                    contractInfo.getAccountID().getAccountNum(),
                    savedContractInfo.getAccountID().getAccountNum());
            Assertions.assertEquals(contractInfo.getStorage(), savedContractInfo.getStorage());
            Assertions.assertEquals(contractInfo.getBalance(), savedContractInfo.getBalance());
        } catch (Exception e) {
            LOG.error("Something amiss with the expected records...", e);
            Assertions.fail("Impossible to meet expectations (on records)!");
        }
    }

    private ContractID readContractID(HapiApiSpec spec) {
        String specExpectationsDir = specScopedDir(spec, validateDirPath);
        try {
            String expectationsDir = specExpectationsDir + "/" + contract;
            File contractIdFile = new File(expectationsDir + "/contractId.txt");
            ByteSource contractIdByteSource = Files.asByteSource(contractIdFile);
            return ContractID.parseFrom(contractIdByteSource.read());
        } catch (Exception e) {
            LOG.error("Something wrong with the expected ContractInfo file", e);
            return null;
        }
    }

    private Query getContractInfoQuery(HapiApiSpec spec, Transaction payment, boolean costOnly) {
        ContractGetInfoQuery contractGetInfo;
        if (getPredefinedId) {
            var contractID = readContractID(spec);
            if (contractID != null) {
                contractGetInfo =
                        ContractGetInfoQuery.newBuilder()
                                .setHeader(
                                        costOnly
                                                ? answerCostHeader(payment)
                                                : answerHeader(payment))
                                .setContractID(contractID)
                                .build();
            } else {
                LOG.error("Couldn't read contractID from saved file");
                return null;
            }
        } else {
            final var builder =
                    ContractGetInfoQuery.newBuilder()
                            .setHeader(
                                    costOnly ? answerCostHeader(payment) : answerHeader(payment));
            if (contract.length() == 40) {
                builder.setContractID(
                        ContractID.newBuilder()
                                .setEvmAddress(ByteString.copyFrom(CommonUtils.unhex(contract))));
            } else {
                builder.setContractID(asContractId(contract, spec));
            }
            contractGetInfo = builder.build();
        }
        return Query.newBuilder().setContractGetInfo(contractGetInfo).build();
    }

    @Override
    protected boolean needsPayment() {
        return true;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("contract", contract);
    }
}
