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
package com.hedera.services.bdd.spec.queries.crypto;

import static com.hedera.services.bdd.spec.assertions.AssertUtils.rethrowSummaryError;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;
import static com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;
import com.hedera.services.bdd.spec.assertions.ErroringAsserts;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.*;
import com.swirlds.common.utility.CommonUtils;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

public class HapiGetAccountInfo extends HapiQueryOp<HapiGetAccountInfo> {
    private static final Logger log = LogManager.getLogger(HapiGetAccountInfo.class);

    private String account;
    @Nullable private String protoSaveLoc = null;
    private boolean loggingHexedCryptoKeys = false;
    private String hexedAliasSource = null;
    private String aliasKeySource = null;
    private Optional<String> registryEntry = Optional.empty();
    private List<String> absentRelationships = new ArrayList<>();
    private List<ExpectedTokenRel> relationships = new ArrayList<>();
    Optional<AccountInfoAsserts> expectations = Optional.empty();
    Optional<BiConsumer<AccountInfo, Logger>> customLog = Optional.empty();
    Optional<LongConsumer> exposingExpiryTo = Optional.empty();
    Optional<LongConsumer> exposingBalanceTo = Optional.empty();
    Optional<Long> ownedNfts = Optional.empty();
    Optional<Integer> maxAutomaticAssociations = Optional.empty();
    Optional<Integer> alreadyUsedAutomaticAssociations = Optional.empty();
    private Optional<Consumer<AccountID>> idObserver = Optional.empty();
    @Nullable private Consumer<byte[]> aliasObserver = null;
    private Optional<Consumer<String>> contractAccountIdObserver = Optional.empty();
    private Optional<Integer> tokenAssociationsCount = Optional.empty();
    private boolean assertAliasKeyMatches = false;
    private boolean assertAccountIDIsNotAlias = false;
    private ReferenceType referenceType;
    private ByteString literalAlias;

    public HapiGetAccountInfo(String account) {
        this(account, ReferenceType.REGISTRY_NAME);
    }

    public HapiGetAccountInfo(String reference, ReferenceType type) {
        this.referenceType = type;
        if (type == ReferenceType.ALIAS_KEY_NAME) {
            aliasKeySource = reference;
        } else if (type == ReferenceType.HEXED_CONTRACT_ALIAS) {
            hexedAliasSource = reference;
        } else {
            account = reference;
        }
    }

    public HapiGetAccountInfo(final ByteString literalAlias) {
        this.referenceType = ReferenceType.LITERAL_ACCOUNT_ALIAS;
        this.literalAlias = literalAlias;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.CryptoGetInfo;
    }

    public HapiGetAccountInfo has(AccountInfoAsserts provider) {
        expectations = Optional.of(provider);
        return this;
    }

    public HapiGetAccountInfo hasExpectedAliasKey() {
        assertAliasKeyMatches = true;
        return this;
    }

    public HapiGetAccountInfo hasExpectedAccountID() {
        assertAccountIDIsNotAlias = true;
        return this;
    }

    public HapiGetAccountInfo plusCustomLog(BiConsumer<AccountInfo, Logger> custom) {
        customLog = Optional.of(custom);
        return this;
    }

    public HapiGetAccountInfo exposingExpiry(LongConsumer obs) {
        this.exposingExpiryTo = Optional.of(obs);
        return this;
    }

    public HapiGetAccountInfo exposingIdTo(Consumer<AccountID> obs) {
        this.idObserver = Optional.of(obs);
        return this;
    }

    public HapiGetAccountInfo exposingAliasTo(Consumer<byte[]> obs) {
        this.aliasObserver = obs;
        return this;
    }

    public HapiGetAccountInfo exposingContractAccountIdTo(Consumer<String> obs) {
        this.contractAccountIdObserver = Optional.of(obs);
        return this;
    }

    public HapiGetAccountInfo exposingBalance(LongConsumer obs) {
        this.exposingBalanceTo = Optional.of(obs);
        return this;
    }

    public HapiGetAccountInfo savingSnapshot(String registryEntry) {
        this.registryEntry = Optional.of(registryEntry);
        return this;
    }

    public HapiGetAccountInfo hasToken(ExpectedTokenRel relationship) {
        relationships.add(relationship);
        return this;
    }

    public HapiGetAccountInfo hasNoTokenRelationship(String token) {
        absentRelationships.add(token);
        return this;
    }

    public HapiGetAccountInfo hasTokenRelationShipCount(int count) {
        tokenAssociationsCount = Optional.of(count);
        return this;
    }

    public HapiGetAccountInfo hasOwnedNfts(long ownedNftsLen) {
        this.ownedNfts = Optional.of(ownedNftsLen);
        return this;
    }

    public HapiGetAccountInfo hasMaxAutomaticAssociations(int max) {
        this.maxAutomaticAssociations = Optional.of(max);
        return this;
    }

    public HapiGetAccountInfo hasAlreadyUsedAutomaticAssociations(int count) {
        this.alreadyUsedAutomaticAssociations = Optional.of(count);
        return this;
    }

    public HapiGetAccountInfo loggingHexedKeys() {
        this.loggingHexedCryptoKeys = true;
        return this;
    }

    public HapiGetAccountInfo savingProtoTo(final String loc) {
        this.protoSaveLoc = loc;
        return this;
    }

    @Override
    protected HapiGetAccountInfo self() {
        return this;
    }

    @Override
    protected void assertExpectationsGiven(HapiSpec spec) throws Throwable {
        final var actualInfo = response.getCryptoGetInfo().getAccountInfo();
        if (assertAliasKeyMatches) {
            Objects.requireNonNull(aliasKeySource);
            final var expected = spec.registry().getKey(aliasKeySource).toByteString();
            assertEquals(expected, actualInfo.getAlias());
        }
        if (assertAccountIDIsNotAlias) {
            Objects.requireNonNull(aliasKeySource);
            final var expectedKeyForAccount =
                    spec.registry().getKey(aliasKeySource).toByteString().toStringUtf8();
            final var expectedID = spec.registry().getAccountID(expectedKeyForAccount);
            Assertions.assertNotEquals(
                    actualInfo.getAlias(), actualInfo.getAccountID().getAccountNum());
            assertEquals(expectedID, actualInfo.getAccountID());
        }
        if (expectations.isPresent()) {
            ErroringAsserts<AccountInfo> asserts = expectations.get().assertsFor(spec);
            List<Throwable> errors = asserts.errorsIn(actualInfo);
            rethrowSummaryError(log, "Bad account info!", errors);
        }
        var actualTokenRels = actualInfo.getTokenRelationshipsList();
        ExpectedTokenRel.assertExpectedRels(account, relationships, actualTokenRels, spec);
        ExpectedTokenRel.assertNoUnexpectedRels(
                account, absentRelationships, actualTokenRels, spec);

        var actualOwnedNfts = actualInfo.getOwnedNfts();
        ownedNfts.ifPresent(nftsOwned -> assertEquals((long) nftsOwned, actualOwnedNfts));

        var actualMaxAutoAssociations = actualInfo.getMaxAutomaticTokenAssociations();
        maxAutomaticAssociations.ifPresent(
                maxAutoAssociations ->
                        assertEquals((int) maxAutoAssociations, actualMaxAutoAssociations));
        alreadyUsedAutomaticAssociations.ifPresent(
                usedCount -> {
                    int actualCount = 0;
                    for (var rel : actualTokenRels) {
                        if (rel.getAutomaticAssociation()) {
                            actualCount++;
                        }
                    }
                    assertEquals(actualCount, usedCount);
                });
        expectedLedgerId.ifPresent(id -> assertEquals(rationalize(id), actualInfo.getLedgerId()));

        tokenAssociationsCount.ifPresent(
                count -> assertEquals(count, actualInfo.getTokenRelationshipsCount()));
    }

    @Override
    protected void submitWith(HapiSpec spec, Transaction payment) throws Throwable {
        Query query = getAccountInfoQuery(spec, payment, false);
        response =
                spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls).getAccountInfo(query);
        final var infoResponse = response.getCryptoGetInfo();
        if (loggingHexedCryptoKeys) {
            log.info("Constituent crypto keys are:");
            visitSimpleKeys(
                    infoResponse.getAccountInfo().getKey(),
                    simpleKey -> {
                        if (!simpleKey.getEd25519().isEmpty()) {
                            log.info("  {}", CommonUtils.hex(simpleKey.getEd25519().toByteArray()));
                        } else if (!simpleKey.getECDSASecp256K1().isEmpty()) {
                            log.info(
                                    "  {}",
                                    CommonUtils.hex(simpleKey.getECDSASecp256K1().toByteArray()));
                        }
                    });
        }
        if (protoSaveLoc != null) {
            final var info = infoResponse.getAccountInfo();
            Files.write(Paths.get(protoSaveLoc), info.toByteArray());
        }
        if (infoResponse.getHeader().getNodeTransactionPrecheckCode() == OK) {
            exposingExpiryTo.ifPresent(
                    cb ->
                            cb.accept(
                                    infoResponse
                                            .getAccountInfo()
                                            .getExpirationTime()
                                            .getSeconds()));
            exposingBalanceTo.ifPresent(
                    cb -> cb.accept(infoResponse.getAccountInfo().getBalance()));
            idObserver.ifPresent(cb -> cb.accept(infoResponse.getAccountInfo().getAccountID()));
            Optional.ofNullable(aliasObserver)
                    .ifPresent(
                            cb ->
                                    cb.accept(
                                            infoResponse
                                                    .getAccountInfo()
                                                    .getAlias()
                                                    .toByteArray()));
            contractAccountIdObserver.ifPresent(
                    cb -> cb.accept(infoResponse.getAccountInfo().getContractAccountID()));
        }
        if (verboseLoggingOn) {
            log.info("Info for '" + repr() + "': " + response.getCryptoGetInfo().getAccountInfo());
        }
        if (customLog.isPresent()) {
            customLog.get().accept(response.getCryptoGetInfo().getAccountInfo(), log);
        }
        if (registryEntry.isPresent()) {
            spec.registry()
                    .saveAccountInfo(
                            registryEntry.get(), response.getCryptoGetInfo().getAccountInfo());
        }
    }

    @Override
    protected long lookupCostWith(HapiSpec spec, Transaction payment) throws Throwable {
        Query query = getAccountInfoQuery(spec, payment, true);
        Response response =
                spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls).getAccountInfo(query);
        return costFrom(response);
    }

    private Query getAccountInfoQuery(HapiSpec spec, Transaction payment, boolean costOnly) {
        AccountID target;
        if (referenceType == ReferenceType.ALIAS_KEY_NAME) {
            target =
                    AccountID.newBuilder()
                            .setAlias(spec.registry().getKey(aliasKeySource).toByteString())
                            .build();
        } else if (referenceType == ReferenceType.HEXED_CONTRACT_ALIAS) {
            target =
                    AccountID.newBuilder()
                            .setAlias(ByteString.copyFrom(CommonUtils.unhex(hexedAliasSource)))
                            .build();
        } else if (referenceType == ReferenceType.LITERAL_ACCOUNT_ALIAS) {
            target = AccountID.newBuilder().setAlias(literalAlias).build();
        } else {
            target = TxnUtils.asId(account, spec);
        }
        CryptoGetInfoQuery query =
                CryptoGetInfoQuery.newBuilder()
                        .setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
                        .setAccountID(target)
                        .build();
        return Query.newBuilder().setCryptoGetInfo(query).build();
    }

    @Override
    protected boolean needsPayment() {
        return true;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("account", account);
    }

    private String repr() {
        if (referenceType == ReferenceType.REGISTRY_NAME) {
            return account;
        } else {
            return "KeyAlias(" + aliasKeySource + ")";
        }
    }

    private static void visitSimpleKeys(final Key key, final Consumer<Key> observer) {
        if (key.hasKeyList()) {
            key.getKeyList().getKeysList().forEach(subKey -> visitSimpleKeys(subKey, observer));
        } else if (key.hasThresholdKey()) {
            key.getThresholdKey()
                    .getKeys()
                    .getKeysList()
                    .forEach(subKey -> visitSimpleKeys(subKey, observer));
        } else {
            observer.accept(key);
        }
    }
}
