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
package com.hedera.services.bdd.suites.token;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.assertions.NoTokenTransfers.emptyTokenTransfers;
import static com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers.changingFungibleBalances;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.*;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Granted;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.KycNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.assertions.BaseErroringAssertsProvider;
import com.hedera.services.bdd.spec.assertions.ErroringAsserts;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenAssociationSpecs extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(TokenAssociationSpecs.class);

    public static final String FREEZABLE_TOKEN_ON_BY_DEFAULT = "TokenA";
    public static final String FREEZABLE_TOKEN_OFF_BY_DEFAULT = "TokenB";
    public static final String KNOWABLE_TOKEN = "TokenC";
    public static final String VANILLA_TOKEN = "TokenD";
    public static final String MULTI_KEY = "multiKey";
    public static final String TBD_TOKEN = "ToBeDeleted";

    public static void main(String... args) {
        final var spec = new TokenAssociationSpecs();

        spec.deferResultsSummary();
        spec.runSuiteAsync();
        spec.summarizeDeferredResults();
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                treasuryAssociationIsAutomatic(),
                dissociateHasExpectedSemantics(),
                associatedContractsMustHaveAdminKeys(),
                expiredAndDeletedTokensStillAppearInContractInfo(),
                dissociationFromExpiredTokensAsExpected(),
                accountInfoQueriesAsExpected(),
                handlesUseOfDefaultTokenId(),
                contractInfoQueriesAsExpected(),
                dissociateHasExpectedSemanticsForDeletedTokens(),
                dissociateHasExpectedSemanticsForDissociatedContracts(),
                canDissociateFromDeletedTokenWithAlreadyDissociatedTreasury(),
                multiAssociationWithSameRepeatedTokenAsExpected());
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    private HapiApiSpec multiAssociationWithSameRepeatedTokenAsExpected() {
        final var nfToken = "nfToken";
        final var civilian = "civilian";
        final var multiAssociate = "multiAssociate";
        final var theContract = "AssociateDissociate";
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> civilianMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("MultiAssociationWithSameRepeatedTokenAsExpected")
                .given(
                        cryptoCreate(civilian)
                                .exposingCreatedIdTo(
                                        id -> civilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        tokenCreate(nfToken)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(GENESIS)
                                .initialSupply(0)
                                .exposingCreatedIdTo(
                                        idLit ->
                                                tokenMirrorAddr.set(
                                                        asHexedSolidityAddress(
                                                                HapiPropertySource.asToken(
                                                                        idLit)))),
                        uploadInitCode(theContract),
                        contractCreate(theContract))
                .when(
                        sourcing(
                                () ->
                                        contractCall(
                                                        theContract,
                                                        "tokensAssociate",
                                                        civilianMirrorAddr.get(),
                                                        List.of(
                                                                tokenMirrorAddr.get(),
                                                                tokenMirrorAddr.get()))
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                .via(multiAssociate)
                                                .payingWith(civilian)
                                                .gas(4_000_000)))
                .then(
                        childRecordsCheck(
                                multiAssociate,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(TOKEN_ID_REPEATED_IN_TOKEN_LIST)),
                        getAccountInfo(civilian).hasNoTokenRelationship(nfToken));
    }

    public HapiApiSpec handlesUseOfDefaultTokenId() {
        return defaultHapiSpec("HandlesUseOfDefaultTokenId")
                .given()
                .when()
                .then(tokenAssociate(DEFAULT_PAYER, "0.0.0").hasKnownStatus(INVALID_TOKEN_ID));
    }

    public HapiApiSpec associatedContractsMustHaveAdminKeys() {
        String misc = "someToken";
        String contract = "defaultContract";

        return defaultHapiSpec("AssociatedContractsMustHaveAdminKeys")
                .given(tokenCreate(misc))
                .when(createDefaultContract(contract).omitAdminKey())
                .then(tokenAssociate(contract, misc).hasKnownStatus(INVALID_SIGNATURE));
    }

    public HapiApiSpec contractInfoQueriesAsExpected() {
        final var contract = "contract";
        return defaultHapiSpec("ContractInfoQueriesAsExpected")
                .given(
                        newKeyNamed("simple"),
                        tokenCreate("a"),
                        tokenCreate("b"),
                        tokenCreate("c"),
                        tokenCreate("tbd").adminKey("simple"),
                        createDefaultContract(contract))
                .when(
                        tokenAssociate(contract, "a", "b", "c", "tbd"),
                        getContractInfo(contract)
                                .hasToken(relationshipWith("a"))
                                .hasToken(relationshipWith("b"))
                                .hasToken(relationshipWith("c"))
                                .hasToken(relationshipWith("tbd")),
                        tokenDissociate(contract, "b"),
                        tokenDelete("tbd"))
                .then(
                        getContractInfo(contract)
                                .hasToken(relationshipWith("a"))
                                .hasNoTokenRelationship("b")
                                .hasToken(relationshipWith("c"))
                                .hasToken(relationshipWith("tbd"))
                                .logged());
    }

    public HapiApiSpec accountInfoQueriesAsExpected() {
        final var account = "account";
        return defaultHapiSpec("InfoQueriesAsExpected")
                .given(
                        newKeyNamed("simple"),
                        tokenCreate("a").decimals(1),
                        tokenCreate("b").decimals(2),
                        tokenCreate("c").decimals(3),
                        tokenCreate("tbd").adminKey("simple").decimals(4),
                        cryptoCreate(account).balance(0L))
                .when(
                        tokenAssociate(account, "a", "b", "c", "tbd"),
                        getAccountInfo(account)
                                .hasToken(relationshipWith("a").decimals(1))
                                .hasToken(relationshipWith("b").decimals(2))
                                .hasToken(relationshipWith("c").decimals(3))
                                .hasToken(relationshipWith("tbd").decimals(4)),
                        tokenDissociate(account, "b"),
                        tokenDelete("tbd"))
                .then(
                        getAccountInfo(account)
                                .hasToken(relationshipWith("a").decimals(1))
                                .hasNoTokenRelationship("b")
                                .hasToken(relationshipWith("c").decimals(3))
                                .hasToken(relationshipWith("tbd").decimals(4))
                                .logged());
    }

    public HapiApiSpec expiredAndDeletedTokensStillAppearInContractInfo() {
        final String contract = "Fuse";
        final String treasury = "something";
        final String expiringToken = "expiringToken";
        final long lifetimeSecs = 10;
        final long xfer = 123L;
        AtomicLong now = new AtomicLong();
        return defaultHapiSpec("ExpiredAndDeletedTokensStillAppearInContractInfo")
                .given(
                        newKeyNamed("admin"),
                        cryptoCreate(treasury),
                        uploadInitCode(contract),
                        contractCreate(contract).gas(300_000).via("creation"),
                        withOpContext(
                                (spec, opLog) -> {
                                    var subOp = getTxnRecord("creation");
                                    allRunFor(spec, subOp);
                                    var record = subOp.getResponseRecord();
                                    now.set(record.getConsensusTimestamp().getSeconds());
                                }),
                        sourcing(
                                () ->
                                        tokenCreate(expiringToken)
                                                .decimals(666)
                                                .adminKey("admin")
                                                .treasury(treasury)
                                                .expiry(now.get() + lifetimeSecs)))
                .when(
                        tokenAssociate(contract, expiringToken),
                        cryptoTransfer(moving(xfer, expiringToken).between(treasury, contract)))
                .then(
                        getAccountBalance(contract).hasTokenBalance(expiringToken, xfer),
                        getContractInfo(contract)
                                .hasToken(
                                        relationshipWith(expiringToken)
                                                .freeze(FreezeNotApplicable)),
                        sleepFor(lifetimeSecs * 1_000L),
                        getAccountBalance(contract).hasTokenBalance(expiringToken, xfer, 666),
                        getContractInfo(contract)
                                .hasToken(
                                        relationshipWith(expiringToken)
                                                .freeze(FreezeNotApplicable)),
                        tokenDelete(expiringToken),
                        getAccountBalance(contract).hasTokenBalance(expiringToken, xfer),
                        getContractInfo(contract)
                                .hasToken(
                                        relationshipWith(expiringToken)
                                                .decimals(666)
                                                .freeze(FreezeNotApplicable)));
    }

    public HapiApiSpec dissociationFromExpiredTokensAsExpected() {
        final String treasury = "accountA";
        final String frozenAccount = "frozen";
        final String unfrozenAccount = "unfrozen";
        final String expiringToken = "expiringToken";
        long lifetimeSecs = 10;

        AtomicLong now = new AtomicLong();
        return defaultHapiSpec("DissociationFromExpiredTokensAsExpected")
                .given(
                        newKeyNamed("freezeKey"),
                        cryptoCreate(treasury),
                        cryptoCreate(frozenAccount).via("creation"),
                        cryptoCreate(unfrozenAccount).via("creation"),
                        withOpContext(
                                (spec, opLog) -> {
                                    var subOp = getTxnRecord("creation");
                                    allRunFor(spec, subOp);
                                    var record = subOp.getResponseRecord();
                                    now.set(record.getConsensusTimestamp().getSeconds());
                                }),
                        sourcing(
                                () ->
                                        tokenCreate(expiringToken)
                                                .freezeKey("freezeKey")
                                                .freezeDefault(true)
                                                .treasury(treasury)
                                                .initialSupply(1000L)
                                                .expiry(now.get() + lifetimeSecs)))
                .when(
                        tokenAssociate(unfrozenAccount, expiringToken),
                        tokenAssociate(frozenAccount, expiringToken),
                        tokenUnfreeze(expiringToken, unfrozenAccount),
                        cryptoTransfer(
                                moving(100L, expiringToken).between(treasury, unfrozenAccount)))
                .then(
                        getAccountBalance(treasury).hasTokenBalance(expiringToken, 900L),
                        sleepFor(lifetimeSecs * 1_000L),
                        tokenDissociate(treasury, expiringToken)
                                .hasKnownStatus(ACCOUNT_IS_TREASURY),
                        tokenDissociate(unfrozenAccount, expiringToken).via("dissociateTxn"),
                        getTxnRecord("dissociateTxn")
                                .hasPriority(
                                        recordWith()
                                                .tokenTransfers(
                                                        new BaseErroringAssertsProvider<>() {
                                                            @Override
                                                            public ErroringAsserts<
                                                                            List<TokenTransferList>>
                                                                    assertsFor(HapiApiSpec spec) {
                                                                return tokenXfers -> {
                                                                    try {
                                                                        assertEquals(
                                                                                1,
                                                                                tokenXfers.size(),
                                                                                "Wrong number of"
                                                                                    + " tokens"
                                                                                    + " transferred!");
                                                                        TokenTransferList xfers =
                                                                                tokenXfers.get(0);
                                                                        assertEquals(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                expiringToken),
                                                                                xfers.getToken(),
                                                                                "Wrong token"
                                                                                    + " transferred!");
                                                                        AccountAmount toTreasury =
                                                                                xfers.getTransfers(
                                                                                        0);
                                                                        assertEquals(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                treasury),
                                                                                toTreasury
                                                                                        .getAccountID(),
                                                                                "Treasury should"
                                                                                    + " come"
                                                                                    + " first!");
                                                                        assertEquals(
                                                                                100L,
                                                                                toTreasury
                                                                                        .getAmount(),
                                                                                "Treasury should"
                                                                                        + " get 100"
                                                                                        + " tokens"
                                                                                        + " back!");
                                                                        AccountAmount fromAccount =
                                                                                xfers.getTransfers(
                                                                                        1);
                                                                        assertEquals(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                unfrozenAccount),
                                                                                fromAccount
                                                                                        .getAccountID(),
                                                                                "Account should"
                                                                                    + " come"
                                                                                    + " second!");
                                                                        assertEquals(
                                                                                -100L,
                                                                                fromAccount
                                                                                        .getAmount(),
                                                                                "Account should"
                                                                                    + " send 100"
                                                                                    + " tokens"
                                                                                    + " back!");
                                                                    } catch (Exception error) {
                                                                        return List.of(error);
                                                                    }
                                                                    return Collections.emptyList();
                                                                };
                                                            }
                                                        })),
                        getAccountBalance(treasury).hasTokenBalance(expiringToken, 1000L),
                        getAccountInfo(frozenAccount)
                                .hasToken(relationshipWith(expiringToken).freeze(Frozen)),
                        tokenDissociate(frozenAccount, expiringToken)
                                .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN));
    }

    public HapiApiSpec canDissociateFromDeletedTokenWithAlreadyDissociatedTreasury() {
        final String aNonTreasuryAcquaintance = "aNonTreasuryAcquaintance";
        final String bNonTreasuryAcquaintance = "bNonTreasuryAcquaintance";
        final long initialSupply = 100L;
        final long nonZeroXfer = 10L;
        final var treasuryDissoc = "treasuryDissoc";
        final var aNonTreasuryDissoc = "aNonTreasuryDissoc";
        final var bNonTreasuryDissoc = "bNonTreasuryDissoc";

        return defaultHapiSpec("CanDissociateFromDeletedTokenWithAlreadyDissociatedTreasury")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        tokenCreate(TBD_TOKEN)
                                .freezeKey(MULTI_KEY)
                                .freezeDefault(false)
                                .adminKey(MULTI_KEY)
                                .initialSupply(initialSupply)
                                .treasury(TOKEN_TREASURY),
                        cryptoCreate(aNonTreasuryAcquaintance).balance(0L),
                        cryptoCreate(bNonTreasuryAcquaintance).maxAutomaticTokenAssociations(1))
                .when(
                        tokenAssociate(aNonTreasuryAcquaintance, TBD_TOKEN),
                        cryptoTransfer(
                                moving(nonZeroXfer, TBD_TOKEN)
                                        .distributing(
                                                TOKEN_TREASURY,
                                                aNonTreasuryAcquaintance,
                                                bNonTreasuryAcquaintance)),
                        tokenFreeze(TBD_TOKEN, aNonTreasuryAcquaintance),
                        tokenDelete(TBD_TOKEN),
                        tokenDissociate(bNonTreasuryAcquaintance, TBD_TOKEN)
                                .via(bNonTreasuryDissoc),
                        tokenDissociate(TOKEN_TREASURY, TBD_TOKEN).via(treasuryDissoc),
                        tokenDissociate(aNonTreasuryAcquaintance, TBD_TOKEN)
                                .via(aNonTreasuryDissoc))
                .then(
                        getTxnRecord(bNonTreasuryDissoc)
                                .hasPriority(
                                        recordWith()
                                                .tokenTransfers(
                                                        changingFungibleBalances()
                                                                .including(
                                                                        TBD_TOKEN,
                                                                        bNonTreasuryAcquaintance,
                                                                        -nonZeroXfer / 2))),
                        getTxnRecord(treasuryDissoc)
                                .hasPriority(
                                        recordWith()
                                                .tokenTransfers(
                                                        changingFungibleBalances()
                                                                .including(
                                                                        TBD_TOKEN,
                                                                        TOKEN_TREASURY,
                                                                        nonZeroXfer
                                                                                - initialSupply))),
                        getTxnRecord(aNonTreasuryDissoc)
                                .hasPriority(
                                        recordWith()
                                                .tokenTransfers(
                                                        changingFungibleBalances()
                                                                .including(
                                                                        TBD_TOKEN,
                                                                        aNonTreasuryAcquaintance,
                                                                        -nonZeroXfer / 2))));
    }

    public HapiApiSpec dissociateHasExpectedSemanticsForDeletedTokens() {
        final String tbdUniqToken = "UniqToBeDeleted";
        final String zeroBalanceFrozen = "0bFrozen";
        final String zeroBalanceUnfrozen = "0bUnfrozen";
        final String nonZeroBalanceFrozen = "1bFrozen";
        final String nonZeroBalanceUnfrozen = "1bUnfrozen";
        final long initialSupply = 100L;
        final long nonZeroXfer = 10L;
        final var zeroBalanceDissoc = "zeroBalanceDissoc";
        final var nonZeroBalanceDissoc = "nonZeroBalanceDissoc";
        final var uniqDissoc = "uniqDissoc";
        final var firstMeta = ByteString.copyFrom("FIRST".getBytes(StandardCharsets.UTF_8));
        final var secondMeta = ByteString.copyFrom("SECOND".getBytes(StandardCharsets.UTF_8));
        final var thirdMeta = ByteString.copyFrom("THIRD".getBytes(StandardCharsets.UTF_8));

        return defaultHapiSpec("DissociateHasExpectedSemanticsForDeletedTokens")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        tokenCreate(TBD_TOKEN)
                                .adminKey(MULTI_KEY)
                                .initialSupply(initialSupply)
                                .treasury(TOKEN_TREASURY)
                                .freezeKey(MULTI_KEY)
                                .freezeDefault(true),
                        tokenCreate(tbdUniqToken)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .initialSupply(0),
                        cryptoCreate(zeroBalanceFrozen).balance(0L),
                        cryptoCreate(zeroBalanceUnfrozen).balance(0L),
                        cryptoCreate(nonZeroBalanceFrozen).balance(0L),
                        cryptoCreate(nonZeroBalanceUnfrozen).balance(0L))
                .when(
                        tokenAssociate(zeroBalanceFrozen, TBD_TOKEN),
                        tokenAssociate(zeroBalanceUnfrozen, TBD_TOKEN),
                        tokenAssociate(nonZeroBalanceFrozen, TBD_TOKEN),
                        tokenAssociate(nonZeroBalanceUnfrozen, TBD_TOKEN),
                        mintToken(tbdUniqToken, List.of(firstMeta, secondMeta, thirdMeta)),
                        getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(3),
                        tokenUnfreeze(TBD_TOKEN, zeroBalanceUnfrozen),
                        tokenUnfreeze(TBD_TOKEN, nonZeroBalanceUnfrozen),
                        tokenUnfreeze(TBD_TOKEN, nonZeroBalanceFrozen),
                        cryptoTransfer(
                                moving(nonZeroXfer, TBD_TOKEN)
                                        .between(TOKEN_TREASURY, nonZeroBalanceFrozen)),
                        cryptoTransfer(
                                moving(nonZeroXfer, TBD_TOKEN)
                                        .between(TOKEN_TREASURY, nonZeroBalanceUnfrozen)),
                        tokenFreeze(TBD_TOKEN, nonZeroBalanceFrozen),
                        getAccountBalance(TOKEN_TREASURY)
                                .hasTokenBalance(TBD_TOKEN, initialSupply - 2 * nonZeroXfer),
                        tokenDelete(TBD_TOKEN),
                        tokenDelete(tbdUniqToken))
                .then(
                        tokenDissociate(zeroBalanceFrozen, TBD_TOKEN).via(zeroBalanceDissoc),
                        tokenDissociate(zeroBalanceUnfrozen, TBD_TOKEN),
                        tokenDissociate(nonZeroBalanceFrozen, TBD_TOKEN).via(nonZeroBalanceDissoc),
                        tokenDissociate(nonZeroBalanceUnfrozen, TBD_TOKEN),
                        tokenDissociate(TOKEN_TREASURY, tbdUniqToken).via(uniqDissoc),
                        getAccountBalance(TOKEN_TREASURY)
                                .hasTokenBalance(TBD_TOKEN, initialSupply - 2 * nonZeroXfer),
                        getTxnRecord(zeroBalanceDissoc)
                                .hasPriority(recordWith().tokenTransfers(emptyTokenTransfers())),
                        getTxnRecord(nonZeroBalanceDissoc)
                                .hasPriority(
                                        recordWith()
                                                .tokenTransfers(
                                                        changingFungibleBalances()
                                                                .including(
                                                                        TBD_TOKEN,
                                                                        nonZeroBalanceFrozen,
                                                                        -nonZeroXfer))),
                        getTxnRecord(uniqDissoc)
                                .hasPriority(
                                        recordWith()
                                                .tokenTransfers(
                                                        changingFungibleBalances()
                                                                .including(
                                                                        tbdUniqToken,
                                                                        TOKEN_TREASURY,
                                                                        -3))),
                        getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(0));
    }

    public HapiApiSpec dissociateHasExpectedSemantics() {
        return defaultHapiSpec("DissociateHasExpectedSemantics")
                .given(basicKeysAndTokens())
                .when(
                        tokenCreate("tkn1").treasury(TOKEN_TREASURY),
                        tokenDissociate(TOKEN_TREASURY, "tkn1").hasKnownStatus(ACCOUNT_IS_TREASURY),
                        cryptoCreate("misc"),
                        tokenDissociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT)
                                .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        tokenAssociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT, KNOWABLE_TOKEN),
                        tokenDissociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT)
                                .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
                        tokenUnfreeze(FREEZABLE_TOKEN_ON_BY_DEFAULT, "misc"),
                        cryptoTransfer(
                                moving(1, FREEZABLE_TOKEN_ON_BY_DEFAULT)
                                        .between(TOKEN_TREASURY, "misc")),
                        tokenDissociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT)
                                .hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES),
                        cryptoTransfer(
                                moving(1, FREEZABLE_TOKEN_ON_BY_DEFAULT)
                                        .between("misc", TOKEN_TREASURY)),
                        tokenDissociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT))
                .then(
                        getAccountInfo("misc")
                                .hasToken(relationshipWith(KNOWABLE_TOKEN))
                                .hasNoTokenRelationship(FREEZABLE_TOKEN_ON_BY_DEFAULT)
                                .logged());
    }

    public HapiApiSpec dissociateHasExpectedSemanticsForDissociatedContracts() {
        final var multiKey = "multiKey";
        final var uniqToken = "UniqToken";
        final var contract = "Fuse";
        final var bytecode = "bytecode";
        final var firstMeta = ByteString.copyFrom("FIRST".getBytes(StandardCharsets.UTF_8));
        final var secondMeta = ByteString.copyFrom("SECOND".getBytes(StandardCharsets.UTF_8));
        final var thirdMeta = ByteString.copyFrom("THIRD".getBytes(StandardCharsets.UTF_8));

        return defaultHapiSpec("DissociateHasExpectedSemanticsForDissociatedContracts")
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(TOKEN_TREASURY).balance(0L).maxAutomaticTokenAssociations(542),
                        uploadInitCode(contract),
                        contractCreate(contract).gas(300_000),
                        tokenCreate(uniqToken)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(multiKey)
                                .treasury(TOKEN_TREASURY),
                        mintToken(uniqToken, List.of(firstMeta, secondMeta, thirdMeta)),
                        getAccountInfo(TOKEN_TREASURY).logged())
                .when(tokenAssociate(contract, uniqToken), tokenDissociate(contract, uniqToken))
                .then(
                        cryptoTransfer(
                                        TokenMovement.movingUnique(uniqToken, 1L)
                                                .between(TOKEN_TREASURY, contract))
                                .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT));
    }

    public HapiApiSpec treasuryAssociationIsAutomatic() {
        return defaultHapiSpec("TreasuryAssociationIsAutomatic")
                .given(basicKeysAndTokens())
                .when()
                .then(
                        getAccountInfo(TOKEN_TREASURY)
                                .hasToken(
                                        relationshipWith(FREEZABLE_TOKEN_ON_BY_DEFAULT)
                                                .kyc(KycNotApplicable)
                                                .freeze(Unfrozen))
                                .hasToken(
                                        relationshipWith(FREEZABLE_TOKEN_OFF_BY_DEFAULT)
                                                .kyc(KycNotApplicable)
                                                .freeze(Unfrozen))
                                .hasToken(
                                        relationshipWith(KNOWABLE_TOKEN)
                                                .kyc(Granted)
                                                .freeze(FreezeNotApplicable))
                                .hasToken(
                                        relationshipWith(VANILLA_TOKEN)
                                                .kyc(KycNotApplicable)
                                                .freeze(FreezeNotApplicable))
                                .logged(),
                        cryptoCreate("test"),
                        tokenAssociate("test", KNOWABLE_TOKEN),
                        tokenAssociate("test", FREEZABLE_TOKEN_OFF_BY_DEFAULT),
                        tokenAssociate("test", FREEZABLE_TOKEN_ON_BY_DEFAULT),
                        tokenAssociate("test", VANILLA_TOKEN),
                        getAccountInfo("test").logged(),
                        tokenDissociate("test", VANILLA_TOKEN),
                        getAccountInfo("test").logged(),
                        tokenDissociate("test", FREEZABLE_TOKEN_OFF_BY_DEFAULT).logged(),
                        getAccountInfo("test").logged());
    }

    public static HapiSpecOperation[] basicKeysAndTokens() {
        return new HapiSpecOperation[] {
            newKeyNamed("kycKey"),
            newKeyNamed("freezeKey"),
            cryptoCreate(TOKEN_TREASURY).balance(0L),
            tokenCreate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
                    .treasury(TOKEN_TREASURY)
                    .freezeKey("freezeKey")
                    .freezeDefault(true),
            tokenCreate(FREEZABLE_TOKEN_OFF_BY_DEFAULT)
                    .treasury(TOKEN_TREASURY)
                    .freezeKey("freezeKey")
                    .freezeDefault(false),
            tokenCreate(KNOWABLE_TOKEN).treasury(TOKEN_TREASURY).kycKey("kycKey"),
            tokenCreate(VANILLA_TOKEN).treasury(TOKEN_TREASURY)
        };
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
