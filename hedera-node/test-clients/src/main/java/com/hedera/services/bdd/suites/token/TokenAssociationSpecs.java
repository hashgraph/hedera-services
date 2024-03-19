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

package com.hedera.services.bdd.suites.token;

import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.NoTokenTransfers.emptyTokenTransfers;
import static com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers.changingFungibleBalances;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createDefaultContract;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.FreezeNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Unfrozen;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Granted;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.KycNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode;
import com.hedera.services.bdd.suites.HapiSuite;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite(fuzzyMatch = true)
@Tag(TOKEN)
public class TokenAssociationSpecs extends HapiSuite {

    private static final Logger log = LogManager.getLogger(TokenAssociationSpecs.class);

    public static final String FREEZABLE_TOKEN_ON_BY_DEFAULT = "TokenA";
    public static final String FREEZABLE_TOKEN_OFF_BY_DEFAULT = "TokenB";
    public static final String KNOWABLE_TOKEN = "TokenC";
    public static final String VANILLA_TOKEN = "TokenD";
    public static final String MULTI_KEY = "multiKey";
    public static final String TBD_TOKEN = "ToBeDeleted";
    public static final String CREATION = "creation";
    public static final String SIMPLE = "simple";
    public static final String FREEZE_KEY = "freezeKey";
    public static final String KYC_KEY = "kycKey";

    public static void main(String... args) {
        final var spec = new TokenAssociationSpecs();

        spec.deferResultsSummary();
        spec.runSuiteAsync();
        spec.summarizeDeferredResults();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                treasuryAssociationIsAutomatic(),
                dissociateHasExpectedSemantics(),
                associatedContractsMustHaveAdminKeys(),
                expiredAndDeletedTokensStillAppearInContractInfo(),
                accountInfoQueriesAsExpected(),
                handlesUseOfDefaultTokenId(),
                contractInfoQueriesAsExpected(),
                dissociateHasExpectedSemanticsForDeletedTokens(),
                dissociateHasExpectedSemanticsForDissociatedContracts(),
                canDissociateFromDeletedTokenWithAlreadyDissociatedTreasury());
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @HapiTest
    public HapiSpec handlesUseOfDefaultTokenId() {
        return defaultHapiSpec("HandlesUseOfDefaultTokenId", SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES)
                .given()
                .when()
                .then(tokenAssociate(DEFAULT_PAYER, "0.0.0").hasKnownStatus(INVALID_TOKEN_ID));
    }

    @HapiTest
    public HapiSpec canDeleteNonFungibleTokenTreasuryAfterUpdate() {
        return defaultHapiSpec("canDeleteNonFungibleTokenTreasuryAfterUpdate")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate("replacementTreasury"),
                        tokenCreate(TBD_TOKEN)
                                .adminKey(MULTI_KEY)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0L)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(MULTI_KEY),
                        mintToken(TBD_TOKEN, List.of(ByteString.copyFromUtf8("1"), ByteString.copyFromUtf8("2"))))
                .when(
                        cryptoDelete(TOKEN_TREASURY).hasKnownStatus(ACCOUNT_IS_TREASURY),
                        tokenAssociate("replacementTreasury", TBD_TOKEN),
                        tokenUpdate(TBD_TOKEN).treasury("replacementTreasury"))
                .then(
                        // Updating the treasury transfers the 2 NFTs to the new
                        // treasury; hence the old treasury has numPositiveBalances=0
                        cryptoDelete(TOKEN_TREASURY));
    }

    @HapiTest
    public HapiSpec canDeleteNonFungibleTokenTreasuryBurnsAndTokenDeletion() {
        final var firstTbdToken = "firstTbdToken";
        final var secondTbdToken = "secondTbdToken";
        final var treasuryWithoutAllPiecesBurned = "treasuryWithoutAllPiecesBurned";
        final var treasuryWithAllPiecesBurned = "treasuryWithAllPiecesBurned";
        return defaultHapiSpec("canDeleteNonFungibleTokenTreasuryBurnsAndTokenDeletion")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(treasuryWithAllPiecesBurned),
                        cryptoCreate(treasuryWithoutAllPiecesBurned),
                        tokenCreate(firstTbdToken)
                                .adminKey(MULTI_KEY)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0L)
                                .treasury(treasuryWithAllPiecesBurned)
                                .supplyKey(MULTI_KEY),
                        tokenCreate(secondTbdToken)
                                .adminKey(MULTI_KEY)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0L)
                                .treasury(treasuryWithoutAllPiecesBurned)
                                .supplyKey(MULTI_KEY),
                        mintToken(firstTbdToken, List.of(ByteString.copyFromUtf8("1"), ByteString.copyFromUtf8("2"))),
                        mintToken(secondTbdToken, List.of(ByteString.copyFromUtf8("1"), ByteString.copyFromUtf8("2"))))
                .when(
                        // Delete both tokens, but only burn all serials for
                        // one of them (so that the other has a treasury that
                        // will need to explicitly dissociate from the deleted
                        // token before it can be deleted)
                        burnToken(firstTbdToken, List.of(1L, 2L)),
                        tokenDelete(firstTbdToken),
                        tokenDelete(secondTbdToken),
                        cryptoDelete(treasuryWithAllPiecesBurned),
                        // This treasury still has numPositiveBalances=1
                        cryptoDelete(treasuryWithoutAllPiecesBurned)
                                .hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES))
                .then(
                        // Now dissociate the second treasury so that it can be deleted
                        tokenDissociate(treasuryWithoutAllPiecesBurned, secondTbdToken),
                        cryptoDelete(treasuryWithoutAllPiecesBurned));
    }

    @HapiTest
    public HapiSpec associatedContractsMustHaveAdminKeys() {
        String misc = "someToken";
        String contract = "defaultContract";

        return defaultHapiSpec("AssociatedContractsMustHaveAdminKeys")
                .given(tokenCreate(misc))
                .when(createDefaultContract(contract).omitAdminKey())
                .then(tokenAssociate(contract, misc).hasKnownStatus(INVALID_SIGNATURE));
    }

    @HapiTest
    public HapiSpec contractInfoQueriesAsExpected() {
        final var contract = "contract";
        return defaultHapiSpec("ContractInfoQueriesAsExpected")
                .given(
                        newKeyNamed(SIMPLE),
                        tokenCreate("a"),
                        tokenCreate("b"),
                        tokenCreate("c"),
                        tokenCreate("tbd").adminKey(SIMPLE),
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
                .then(getContractInfo(contract)
                        .hasToken(relationshipWith("a"))
                        .hasNoTokenRelationship("b")
                        .hasToken(relationshipWith("c"))
                        .hasToken(relationshipWith("tbd"))
                        .logged());
    }

    @HapiTest
    public HapiSpec accountInfoQueriesAsExpected() {
        final var account = "account";
        return defaultHapiSpec("accountInfoQueriesAsExpected")
                .given(
                        newKeyNamed(SIMPLE),
                        tokenCreate("a").decimals(1),
                        tokenCreate("b").decimals(2),
                        tokenCreate("c").decimals(3),
                        tokenCreate("tbd").adminKey(SIMPLE).decimals(4),
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
                .then(getAccountInfo(account)
                        .hasToken(relationshipWith("a").decimals(1))
                        .hasNoTokenRelationship("b")
                        .hasToken(relationshipWith("c").decimals(3))
                        .hasToken(relationshipWith("tbd").decimals(4))
                        .logged());
    }

    @HapiTest
    public HapiSpec expiredAndDeletedTokensStillAppearInContractInfo() {
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
                        contractCreate(contract).gas(600_000).via(CREATION),
                        withOpContext((spec, opLog) -> {
                            var subOp = getTxnRecord(CREATION);
                            allRunFor(spec, subOp);
                            var record = subOp.getResponseRecord();
                            now.set(record.getConsensusTimestamp().getSeconds());
                        }),
                        sourcing(() -> tokenCreate(expiringToken)
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
                                .hasToken(relationshipWith(expiringToken).freeze(FreezeNotApplicable)),
                        sleepFor(lifetimeSecs * 1_000L),
                        getAccountBalance(contract).hasTokenBalance(expiringToken, xfer, 666),
                        getContractInfo(contract)
                                .hasToken(relationshipWith(expiringToken).freeze(FreezeNotApplicable)),
                        tokenDelete(expiringToken),
                        getAccountBalance(contract).hasTokenBalance(expiringToken, xfer),
                        getContractInfo(contract)
                                .hasToken(relationshipWith(expiringToken)
                                        .decimals(666)
                                        .freeze(FreezeNotApplicable)));
    }

    @HapiTest
    public HapiSpec canDissociateFromDeletedTokenWithAlreadyDissociatedTreasury() {
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
                        cryptoTransfer(moving(nonZeroXfer, TBD_TOKEN)
                                .distributing(TOKEN_TREASURY, aNonTreasuryAcquaintance, bNonTreasuryAcquaintance)),
                        tokenFreeze(TBD_TOKEN, aNonTreasuryAcquaintance),
                        tokenDelete(TBD_TOKEN),
                        tokenDissociate(bNonTreasuryAcquaintance, TBD_TOKEN).via(bNonTreasuryDissoc),
                        tokenDissociate(TOKEN_TREASURY, TBD_TOKEN).via(treasuryDissoc),
                        tokenDissociate(aNonTreasuryAcquaintance, TBD_TOKEN).via(aNonTreasuryDissoc))
                .then(
                        getTxnRecord(bNonTreasuryDissoc)
                                .hasPriority(recordWith()
                                        .tokenTransfers(changingFungibleBalances()
                                                .including(TBD_TOKEN, bNonTreasuryAcquaintance, -nonZeroXfer / 2))),
                        getTxnRecord(treasuryDissoc)
                                .hasPriority(recordWith()
                                        .tokenTransfers(changingFungibleBalances()
                                                .including(TBD_TOKEN, TOKEN_TREASURY, nonZeroXfer - initialSupply))),
                        getTxnRecord(aNonTreasuryDissoc)
                                .hasPriority(recordWith()
                                        .tokenTransfers(changingFungibleBalances()
                                                .including(TBD_TOKEN, aNonTreasuryAcquaintance, -nonZeroXfer / 2))));
    }

    @HapiTest
    public HapiSpec dissociateHasExpectedSemanticsForDeletedTokens() {
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
                        cryptoTransfer(moving(nonZeroXfer, TBD_TOKEN).between(TOKEN_TREASURY, nonZeroBalanceFrozen)),
                        cryptoTransfer(moving(nonZeroXfer, TBD_TOKEN).between(TOKEN_TREASURY, nonZeroBalanceUnfrozen)),
                        tokenFreeze(TBD_TOKEN, nonZeroBalanceFrozen),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TBD_TOKEN, initialSupply - 2 * nonZeroXfer),
                        tokenDelete(TBD_TOKEN),
                        tokenDelete(tbdUniqToken))
                .then(
                        tokenDissociate(zeroBalanceFrozen, TBD_TOKEN).via(zeroBalanceDissoc),
                        tokenDissociate(zeroBalanceUnfrozen, TBD_TOKEN),
                        tokenDissociate(nonZeroBalanceFrozen, TBD_TOKEN).via(nonZeroBalanceDissoc),
                        tokenDissociate(nonZeroBalanceUnfrozen, TBD_TOKEN),
                        tokenDissociate(TOKEN_TREASURY, tbdUniqToken).via(uniqDissoc),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TBD_TOKEN, initialSupply - 2 * nonZeroXfer),
                        getTxnRecord(zeroBalanceDissoc).hasPriority(recordWith().tokenTransfers(emptyTokenTransfers())),
                        getTxnRecord(nonZeroBalanceDissoc)
                                .hasPriority(recordWith()
                                        .tokenTransfers(changingFungibleBalances()
                                                .including(TBD_TOKEN, nonZeroBalanceFrozen, -nonZeroXfer))),
                        getTxnRecord(uniqDissoc)
                                .hasPriority(recordWith()
                                        .tokenTransfers(changingFungibleBalances()
                                                .including(tbdUniqToken, TOKEN_TREASURY, -3))),
                        getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(0));
    }

    @HapiTest
    public HapiSpec dissociateHasExpectedSemantics() {
        return defaultHapiSpec("DissociateHasExpectedSemantics")
                .given(basicKeysAndTokens())
                .when(
                        tokenCreate("tkn1").treasury(TOKEN_TREASURY),
                        tokenDissociate(TOKEN_TREASURY, "tkn1").hasKnownStatus(ACCOUNT_IS_TREASURY),
                        cryptoCreate("misc"),
                        tokenDissociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT)
                                .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        tokenAssociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT, KNOWABLE_TOKEN),
                        tokenDissociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT).hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
                        tokenUnfreeze(FREEZABLE_TOKEN_ON_BY_DEFAULT, "misc"),
                        cryptoTransfer(moving(1, FREEZABLE_TOKEN_ON_BY_DEFAULT).between(TOKEN_TREASURY, "misc")),
                        tokenDissociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT)
                                .hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES),
                        cryptoTransfer(moving(1, FREEZABLE_TOKEN_ON_BY_DEFAULT).between("misc", TOKEN_TREASURY)),
                        tokenDissociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT))
                .then(getAccountInfo("misc")
                        .hasToken(relationshipWith(KNOWABLE_TOKEN))
                        .hasNoTokenRelationship(FREEZABLE_TOKEN_ON_BY_DEFAULT)
                        .logged());
    }

    @HapiTest
    public HapiSpec dissociateHasExpectedSemanticsForDissociatedContracts() {
        final var uniqToken = "UniqToken";
        final var contract = "Fuse";
        final var firstMeta = ByteString.copyFrom("FIRST".getBytes(StandardCharsets.UTF_8));
        final var secondMeta = ByteString.copyFrom("SECOND".getBytes(StandardCharsets.UTF_8));
        final var thirdMeta = ByteString.copyFrom("THIRD".getBytes(StandardCharsets.UTF_8));

        return defaultHapiSpec("DissociateHasExpectedSemanticsForDissociatedContracts")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY).balance(0L).maxAutomaticTokenAssociations(542),
                        uploadInitCode(contract),
                        contractCreate(contract).gas(600_000),
                        tokenCreate(uniqToken)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(MULTI_KEY)
                                .treasury(TOKEN_TREASURY),
                        mintToken(uniqToken, List.of(firstMeta, secondMeta, thirdMeta)),
                        getAccountInfo(TOKEN_TREASURY).logged())
                .when(tokenAssociate(contract, uniqToken), tokenDissociate(contract, uniqToken))
                .then(cryptoTransfer(TokenMovement.movingUnique(uniqToken, 1L).between(TOKEN_TREASURY, contract))
                        .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT));
    }

    @HapiTest
    public HapiSpec treasuryAssociationIsAutomatic() {
        return defaultHapiSpec("TreasuryAssociationIsAutomatic")
                .given(basicKeysAndTokens())
                .when()
                .then(
                        getAccountInfo(TOKEN_TREASURY)
                                .hasToken(relationshipWith(FREEZABLE_TOKEN_ON_BY_DEFAULT)
                                        .kyc(KycNotApplicable)
                                        .freeze(Unfrozen))
                                .hasToken(relationshipWith(FREEZABLE_TOKEN_OFF_BY_DEFAULT)
                                        .kyc(KycNotApplicable)
                                        .freeze(Unfrozen))
                                .hasToken(relationshipWith(KNOWABLE_TOKEN)
                                        .kyc(Granted)
                                        .freeze(FreezeNotApplicable))
                                .hasToken(relationshipWith(VANILLA_TOKEN)
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
            newKeyNamed(KYC_KEY),
            newKeyNamed(FREEZE_KEY),
            cryptoCreate(TOKEN_TREASURY).balance(0L),
            tokenCreate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
                    .treasury(TOKEN_TREASURY)
                    .freezeKey(FREEZE_KEY)
                    .freezeDefault(true),
            tokenCreate(FREEZABLE_TOKEN_OFF_BY_DEFAULT)
                    .treasury(TOKEN_TREASURY)
                    .freezeKey(FREEZE_KEY)
                    .freezeDefault(false),
            tokenCreate(KNOWABLE_TOKEN).treasury(TOKEN_TREASURY).kycKey(KYC_KEY),
            tokenCreate(VANILLA_TOKEN).treasury(TOKEN_TREASURY)
        };
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
