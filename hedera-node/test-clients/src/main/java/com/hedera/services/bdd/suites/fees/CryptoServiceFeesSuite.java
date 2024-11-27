package com.hedera.services.bdd.suites.fees;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDeleteAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoApproveAllowance.MISSING_OWNER;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.HapiSuite.TOKEN_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_MAX_AUTO_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class CryptoServiceFeesSuite {
    public static final String CIVILIAN = "civilian";
    private static final String SUPPLY_KEY = "supplyKey";
    public static final String OWNER = "owner";
    public static final String FUNGIBLE_TOKEN = "fungible";
    public static final String SPENDER = "spender";
    public static final String ANOTHER_SPENDER = "spender1";
    public static final String SECOND_SPENDER = "spender2";
    public static final String NON_FUNGIBLE_TOKEN = "nonFungible";
    public static final String NFT_TOKEN_MINT_TXN = "nftTokenMint";
    public static final String FUNGIBLE_TOKEN_MINT_TXN = "tokenMint";
    public static final String APPROVE_TXN = "approveTxn";
    private static final String SENDER = "sender";
    private static final String RECEIVER = "receiver";

    @HapiTest
    final Stream<DynamicTest> cryptoCreateUsdFeeAsExpected() {
        double expectedPriceUsd = 0.05;
        final var noAutoAssocSlots = "noAutoAssocSlots";
        final var oneAutoAssocSlot = "oneAutoAssocSlot";
        final var tenAutoAssocSlots = "tenAutoAssocSlots";
        final var negativeAutoAssocSlots = "negativeAutoAssocSlots";
        final var positiveOverflowAutoAssocSlots = "positiveOverflowAutoAssocSlots";
        final var unlimitedAutoAssocSlots = "unlimitedAutoAssocSlots";
        final var token = "token";
        return hapiTest(
                cryptoCreate(CIVILIAN).balance(5 * ONE_HUNDRED_HBARS),
                getAccountBalance(CIVILIAN).hasTinyBars(5 * ONE_HUNDRED_HBARS),
                tokenCreate(token).autoRenewPeriod(THREE_MONTHS_IN_SECONDS),
                cryptoCreate("neverToBe")
                        .balance(0L)
                        .memo("")
                        .entityMemo("")
                        .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                        .payingWith(CIVILIAN)
                        .feeUsd(0.01)
                        .hasPrecheck(INSUFFICIENT_TX_FEE),
                getAccountBalance(CIVILIAN).hasTinyBars(5 * ONE_HUNDRED_HBARS),
                cryptoCreate(noAutoAssocSlots)
                        .key(CIVILIAN)
                        .balance(0L)
                        .via(noAutoAssocSlots)
                        .blankMemo()
                        .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                        .signedBy(CIVILIAN)
                        .payingWith(CIVILIAN),
                cryptoCreate(oneAutoAssocSlot)
                        .key(CIVILIAN)
                        .balance(0L)
                        .maxAutomaticTokenAssociations(1)
                        .via(oneAutoAssocSlot)
                        .blankMemo()
                        .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                        .signedBy(CIVILIAN)
                        .payingWith(CIVILIAN),
                cryptoCreate(tenAutoAssocSlots)
                        .key(CIVILIAN)
                        .balance(0L)
                        .maxAutomaticTokenAssociations(10)
                        .via(tenAutoAssocSlots)
                        .blankMemo()
                        .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                        .signedBy(CIVILIAN)
                        .payingWith(CIVILIAN),
                cryptoCreate(negativeAutoAssocSlots)
                        .key(CIVILIAN)
                        .balance(0L)
                        .maxAutomaticTokenAssociations(-2)
                        .via(negativeAutoAssocSlots)
                        .blankMemo()
                        .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                        .signedBy(CIVILIAN)
                        .payingWith(CIVILIAN)
                        .logged()
                        .hasPrecheck(INVALID_MAX_AUTO_ASSOCIATIONS),
                cryptoCreate(positiveOverflowAutoAssocSlots)
                        .key(CIVILIAN)
                        .balance(0L)
                        .maxAutomaticTokenAssociations(5001)
                        .via(positiveOverflowAutoAssocSlots)
                        .blankMemo()
                        .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                        .signedBy(CIVILIAN)
                        .payingWith(CIVILIAN)
                        .logged()
                        .hasKnownStatus(INVALID_MAX_AUTO_ASSOCIATIONS),
                cryptoCreate(unlimitedAutoAssocSlots)
                        .key(CIVILIAN)
                        .balance(0L)
                        .maxAutomaticTokenAssociations(-1)
                        .via(unlimitedAutoAssocSlots)
                        .blankMemo()
                        .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                        .signedBy(CIVILIAN)
                        .payingWith(CIVILIAN),
                getTxnRecord(tenAutoAssocSlots).logged(),
                validateChargedUsd(noAutoAssocSlots, expectedPriceUsd),
                getAccountInfo(noAutoAssocSlots).hasMaxAutomaticAssociations(0),
                validateChargedUsd(oneAutoAssocSlot, expectedPriceUsd),
                getAccountInfo(oneAutoAssocSlot).hasMaxAutomaticAssociations(1),
                validateChargedUsd(tenAutoAssocSlots, expectedPriceUsd),
                getAccountInfo(tenAutoAssocSlots).hasMaxAutomaticAssociations(10),
                validateChargedUsd(unlimitedAutoAssocSlots, expectedPriceUsd),
                getAccountInfo(unlimitedAutoAssocSlots).hasMaxAutomaticAssociations(-1));
    }

    @HapiTest
    final Stream<DynamicTest> cryptoDeleteAllowanceFeesAsExpected() {
        final String owner = "owner";
        final String spender = "spender";
        final String token = "token";
        final String nft = "nft";
        final String payer = "payer";
        return hapiTest(
                newKeyNamed("supplyKey"),
                cryptoCreate(owner).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                cryptoCreate(payer).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                cryptoCreate(spender).balance(ONE_HUNDRED_HBARS),
                cryptoCreate("spender1").balance(ONE_HUNDRED_HBARS),
                cryptoCreate("spender2").balance(ONE_HUNDRED_HBARS),
                cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                tokenCreate(token)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyType(TokenSupplyType.FINITE)
                        .supplyKey("supplyKey")
                        .maxSupply(1000L)
                        .initialSupply(10L)
                        .treasury(TOKEN_TREASURY),
                tokenCreate(nft)
                        .maxSupply(10L)
                        .initialSupply(0)
                        .supplyType(TokenSupplyType.FINITE)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .supplyKey("supplyKey")
                        .treasury(TOKEN_TREASURY),
                tokenAssociate(owner, token),
                tokenAssociate(owner, nft),
                mintToken(
                        nft,
                        List.of(
                                ByteString.copyFromUtf8("a"),
                                ByteString.copyFromUtf8("b"),
                                ByteString.copyFromUtf8("c")))
                        .via("nftTokenMint"),
                mintToken(token, 500L).via("tokenMint"),
                cryptoTransfer(movingUnique(nft, 1L, 2L, 3L).between(TOKEN_TREASURY, owner)),
                cryptoApproveAllowance()
                        .payingWith(owner)
                        .addCryptoAllowance(owner, "spender2", 100L)
                        .addTokenAllowance(owner, token, "spender2", 100L)
                        .addNftAllowance(owner, nft, "spender2", false, List.of(1L, 2L, 3L)),
                /* without specifying owner */
                cryptoDeleteAllowance()
                        .payingWith(owner)
                        .blankMemo()
                        .addNftDeleteAllowance(MISSING_OWNER, nft, List.of(1L))
                        .via("baseDeleteNft"),
                validateChargedUsdWithin("baseDeleteNft", 0.05, 0.01),
                cryptoApproveAllowance().payingWith(owner).addNftAllowance(owner, nft, "spender2", false, List.of(1L)),
                /* with specifying owner */
                cryptoDeleteAllowance()
                        .payingWith(owner)
                        .blankMemo()
                        .addNftDeleteAllowance(owner, nft, List.of(1L))
                        .via("baseDeleteNft"),
                validateChargedUsdWithin("baseDeleteNft", 0.05, 0.01),

                /* with 2 serials */
                cryptoDeleteAllowance()
                        .payingWith(owner)
                        .blankMemo()
                        .addNftDeleteAllowance(owner, nft, List.of(2L, 3L))
                        .via("twoDeleteNft"),
                validateChargedUsdWithin("twoDeleteNft", 0.050101, 0.01),
                /* with 2 sigs */
                cryptoApproveAllowance().payingWith(owner).addNftAllowance(owner, nft, "spender2", false, List.of(1L)),
                cryptoDeleteAllowance()
                        .payingWith(payer)
                        .blankMemo()
                        .addNftDeleteAllowance(owner, nft, List.of(1L))
                        .signedBy(payer, owner)
                        .via("twoDeleteNft"),
                validateChargedUsdWithin("twoDeleteNft", 0.08124, 0.01));
    }

    @HapiTest
    final Stream<DynamicTest> cryptoApproveAllowanceFeesAsExpected() {
        return hapiTest(
                newKeyNamed(SUPPLY_KEY),
                cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(ANOTHER_SPENDER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(SECOND_SPENDER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                tokenCreate(FUNGIBLE_TOKEN)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyType(TokenSupplyType.FINITE)
                        .supplyKey(SUPPLY_KEY)
                        .maxSupply(1000L)
                        .initialSupply(10L)
                        .treasury(TOKEN_TREASURY),
                tokenCreate(NON_FUNGIBLE_TOKEN)
                        .maxSupply(10L)
                        .initialSupply(0)
                        .supplyType(TokenSupplyType.FINITE)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .supplyKey(SUPPLY_KEY)
                        .treasury(TOKEN_TREASURY),
                tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                mintToken(
                        NON_FUNGIBLE_TOKEN,
                        List.of(
                                ByteString.copyFromUtf8("a"),
                                ByteString.copyFromUtf8("b"),
                                ByteString.copyFromUtf8("c")))
                        .via(NFT_TOKEN_MINT_TXN),
                mintToken(FUNGIBLE_TOKEN, 500L).via(FUNGIBLE_TOKEN_MINT_TXN),
                cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L).between(TOKEN_TREASURY, OWNER)),
                cryptoApproveAllowance()
                        .payingWith(OWNER)
                        .addCryptoAllowance(OWNER, SPENDER, 100L)
                        .via("approve")
                        .fee(ONE_HBAR)
                        .blankMemo()
                        .logged(),
                validateChargedUsdWithin("approve", 0.05, 0.01),
                cryptoApproveAllowance()
                        .payingWith(OWNER)
                        .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                        .via("approveTokenTxn")
                        .fee(ONE_HBAR)
                        .blankMemo()
                        .logged(),
                validateChargedUsdWithin("approveTokenTxn", 0.05012, 0.01),
                cryptoApproveAllowance()
                        .payingWith(OWNER)
                        .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                        .via("approveNftTxn")
                        .fee(ONE_HBAR)
                        .blankMemo()
                        .logged(),
                validateChargedUsdWithin("approveNftTxn", 0.050101, 0.01),
                cryptoApproveAllowance()
                        .payingWith(OWNER)
                        .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, ANOTHER_SPENDER, true, List.of())
                        .via("approveForAllNftTxn")
                        .fee(ONE_HBAR)
                        .blankMemo()
                        .logged(),
                validateChargedUsdWithin("approveForAllNftTxn", 0.05, 0.01),
                cryptoApproveAllowance()
                        .payingWith(OWNER)
                        .addCryptoAllowance(OWNER, SECOND_SPENDER, 100L)
                        .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SECOND_SPENDER, 100L)
                        .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SECOND_SPENDER, false, List.of(1L))
                        .via(APPROVE_TXN)
                        .fee(ONE_HBAR)
                        .blankMemo()
                        .logged(),
                validateChargedUsdWithin(APPROVE_TXN, 0.05238, 0.01),
                getAccountDetails(OWNER)
                        .payingWith(GENESIS)
                        .has(accountDetailsWith()
                                .cryptoAllowancesCount(2)
                                .nftApprovedForAllAllowancesCount(1)
                                .tokenAllowancesCount(2)
                                .cryptoAllowancesContaining(SECOND_SPENDER, 100L)
                                .tokenAllowancesContaining(FUNGIBLE_TOKEN, SECOND_SPENDER, 100L)),
                /* edit existing allowances */
                cryptoApproveAllowance()
                        .payingWith(OWNER)
                        .addCryptoAllowance(OWNER, SECOND_SPENDER, 200L)
                        .via("approveModifyCryptoTxn")
                        .fee(ONE_HBAR)
                        .blankMemo()
                        .logged(),
                validateChargedUsdWithin("approveModifyCryptoTxn", 0.049375, 0.01),
                cryptoApproveAllowance()
                        .payingWith(OWNER)
                        .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SECOND_SPENDER, 200L)
                        .via("approveModifyTokenTxn")
                        .fee(ONE_HBAR)
                        .blankMemo()
                        .logged(),
                validateChargedUsdWithin("approveModifyTokenTxn", 0.04943, 0.01),
                cryptoApproveAllowance()
                        .payingWith(OWNER)
                        .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, ANOTHER_SPENDER, false, List.of())
                        .via("approveModifyNftTxn")
                        .fee(ONE_HBAR)
                        .blankMemo()
                        .logged(),
                validateChargedUsdWithin("approveModifyNftTxn", 0.049375, 0.01),
                getAccountDetails(OWNER)
                        .payingWith(GENESIS)
                        .has(accountDetailsWith()
                                .cryptoAllowancesCount(2)
                                .nftApprovedForAllAllowancesCount(0)
                                .tokenAllowancesCount(2)
                                .cryptoAllowancesContaining(SECOND_SPENDER, 200L)
                                .tokenAllowancesContaining(FUNGIBLE_TOKEN, SECOND_SPENDER, 200L)));
    }

    @LeakyHapiTest(overrides = {"entities.maxLifetime", "ledger.maxAutoAssociations"})
    final Stream<DynamicTest> usdFeeAsExpectedCryptoUpdate() {
        double baseFee = 0.000214;
        double baseFeeWithExpiry = 0.00022;

        final var baseTxn = "baseTxn";
        final var plusOneTxn = "plusOneTxn";
        final var plusTenTxn = "plusTenTxn";
        final var plusFiveKTxn = "plusFiveKTxn";
        final var plusFiveKAndOneTxn = "plusFiveKAndOneTxn";
        final var invalidNegativeTxn = "invalidNegativeTxn";
        final var validNegativeTxn = "validNegativeTxn";
        final var allowedPercentDiff = 1.5;

        AtomicLong expiration = new AtomicLong();
        return hapiTest(
                overridingTwo(
                        "ledger.maxAutoAssociations", "5000",
                        "entities.maxLifetime", "3153600000"),
                newKeyNamed("key").shape(SIMPLE),
                cryptoCreate("payer").key("key").balance(1_000 * ONE_HBAR),
                cryptoCreate("canonicalAccount")
                        .key("key")
                        .balance(100 * ONE_HBAR)
                        .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                        .blankMemo()
                        .payingWith("payer"),
                cryptoCreate("autoAssocTarget")
                        .key("key")
                        .balance(100 * ONE_HBAR)
                        .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                        .blankMemo()
                        .payingWith("payer"),
                getAccountInfo("canonicalAccount").exposingExpiry(expiration::set),
                sourcing(() -> cryptoUpdate("canonicalAccount")
                        .payingWith("canonicalAccount")
                        .expiring(expiration.get() + THREE_MONTHS_IN_SECONDS)
                        .blankMemo()
                        .via(baseTxn)),
                getAccountInfo("canonicalAccount")
                        .hasMaxAutomaticAssociations(0)
                        .logged(),
                cryptoUpdate("autoAssocTarget")
                        .payingWith("autoAssocTarget")
                        .blankMemo()
                        .maxAutomaticAssociations(1)
                        .via(plusOneTxn),
                getAccountInfo("autoAssocTarget").hasMaxAutomaticAssociations(1).logged(),
                cryptoUpdate("autoAssocTarget")
                        .payingWith("autoAssocTarget")
                        .blankMemo()
                        .maxAutomaticAssociations(11)
                        .via(plusTenTxn),
                getAccountInfo("autoAssocTarget")
                        .hasMaxAutomaticAssociations(11)
                        .logged(),
                cryptoUpdate("autoAssocTarget")
                        .payingWith("autoAssocTarget")
                        .blankMemo()
                        .maxAutomaticAssociations(5000)
                        .via(plusFiveKTxn),
                getAccountInfo("autoAssocTarget")
                        .hasMaxAutomaticAssociations(5000)
                        .logged(),
                cryptoUpdate("autoAssocTarget")
                        .payingWith("autoAssocTarget")
                        .blankMemo()
                        .maxAutomaticAssociations(-1000)
                        .via(invalidNegativeTxn)
                        .hasKnownStatus(INVALID_MAX_AUTO_ASSOCIATIONS),
                cryptoUpdate("autoAssocTarget")
                        .payingWith("autoAssocTarget")
                        .blankMemo()
                        .maxAutomaticAssociations(5001)
                        .via(plusFiveKAndOneTxn)
                        .hasKnownStatus(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT),
                cryptoUpdate("autoAssocTarget")
                        .payingWith("autoAssocTarget")
                        .blankMemo()
                        .maxAutomaticAssociations(-1)
                        .via(validNegativeTxn),
                getAccountInfo("autoAssocTarget")
                        .hasMaxAutomaticAssociations(-1)
                        .logged(),
                validateChargedUsd(baseTxn, baseFeeWithExpiry, allowedPercentDiff),
                validateChargedUsd(plusOneTxn, baseFee, allowedPercentDiff),
                validateChargedUsd(plusTenTxn, baseFee, allowedPercentDiff),
                validateChargedUsd(plusFiveKTxn, baseFee, allowedPercentDiff),
                validateChargedUsd(validNegativeTxn, baseFee, allowedPercentDiff));
    }

    @HapiTest
    final Stream<DynamicTest> baseCryptoTransferFeeChargedAsExpected() {
        final var expectedHbarXferPriceUsd = 0.0001;
        final var expectedHtsXferPriceUsd = 0.001;
        final var expectedNftXferPriceUsd = 0.001;
        final var expectedHtsXferWithCustomFeePriceUsd = 0.002;
        final var expectedNftXferWithCustomFeePriceUsd = 0.002;
        final var transferAmount = 1L;
        final var customFeeCollector = "customFeeCollector";
        final var nonTreasurySender = "nonTreasurySender";
        final var hbarXferTxn = "hbarXferTxn";
        final var fungibleToken = "fungibleToken";
        final var fungibleTokenWithCustomFee = "fungibleTokenWithCustomFee";
        final var htsXferTxn = "htsXferTxn";
        final var htsXferTxnWithCustomFee = "htsXferTxnWithCustomFee";
        final var nonFungibleToken = "nonFungibleToken";
        final var nonFungibleTokenWithCustomFee = "nonFungibleTokenWithCustomFee";
        final var nftXferTxn = "nftXferTxn";
        final var nftXferTxnWithCustomFee = "nftXferTxnWithCustomFee";

        return hapiTest(
                cryptoCreate(nonTreasurySender).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(RECEIVER),
                cryptoCreate(customFeeCollector),
                tokenCreate(fungibleToken)
                        .treasury(SENDER)
                        .tokenType(FUNGIBLE_COMMON)
                        .initialSupply(100L),
                tokenCreate(fungibleTokenWithCustomFee)
                        .treasury(SENDER)
                        .tokenType(FUNGIBLE_COMMON)
                        .withCustom(fixedHbarFee(transferAmount, customFeeCollector))
                        .initialSupply(100L),
                tokenAssociate(RECEIVER, fungibleToken, fungibleTokenWithCustomFee),
                newKeyNamed(SUPPLY_KEY),
                tokenCreate(nonFungibleToken)
                        .initialSupply(0)
                        .supplyKey(SUPPLY_KEY)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .treasury(SENDER),
                tokenCreate(nonFungibleTokenWithCustomFee)
                        .initialSupply(0)
                        .supplyKey(SUPPLY_KEY)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .withCustom(fixedHbarFee(transferAmount, customFeeCollector))
                        .treasury(SENDER),
                tokenAssociate(nonTreasurySender, List.of(fungibleTokenWithCustomFee, nonFungibleTokenWithCustomFee)),
                mintToken(nonFungibleToken, List.of(copyFromUtf8("memo1"))),
                mintToken(nonFungibleTokenWithCustomFee, List.of(copyFromUtf8("memo2"))),
                tokenAssociate(RECEIVER, nonFungibleToken, nonFungibleTokenWithCustomFee),
                cryptoTransfer(movingUnique(nonFungibleTokenWithCustomFee, 1).between(SENDER, nonTreasurySender))
                        .payingWith(SENDER),
                cryptoTransfer(moving(1, fungibleTokenWithCustomFee).between(SENDER, nonTreasurySender))
                        .payingWith(SENDER),
                cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 100L))
                        .payingWith(SENDER)
                        .blankMemo()
                        .via(hbarXferTxn),
                cryptoTransfer(moving(1, fungibleToken).between(SENDER, RECEIVER))
                        .blankMemo()
                        .payingWith(SENDER)
                        .via(htsXferTxn),
                cryptoTransfer(movingUnique(nonFungibleToken, 1).between(SENDER, RECEIVER))
                        .blankMemo()
                        .payingWith(SENDER)
                        .via(nftXferTxn),
                cryptoTransfer(moving(1, fungibleTokenWithCustomFee).between(nonTreasurySender, RECEIVER))
                        .blankMemo()
                        .fee(ONE_HBAR)
                        .payingWith(nonTreasurySender)
                        .via(htsXferTxnWithCustomFee),
                cryptoTransfer(movingUnique(nonFungibleTokenWithCustomFee, 1).between(nonTreasurySender, RECEIVER))
                        .blankMemo()
                        .fee(ONE_HBAR)
                        .payingWith(nonTreasurySender)
                        .via(nftXferTxnWithCustomFee),
                validateChargedUsdWithin(hbarXferTxn, expectedHbarXferPriceUsd, 0.01),
                validateChargedUsdWithin(htsXferTxn, expectedHtsXferPriceUsd, 0.01),
                validateChargedUsdWithin(nftXferTxn, expectedNftXferPriceUsd, 0.01),
                validateChargedUsdWithin(htsXferTxnWithCustomFee, expectedHtsXferWithCustomFeePriceUsd, 0.1),
                validateChargedUsdWithin(nftXferTxnWithCustomFee, expectedNftXferWithCustomFeePriceUsd, 0.3));
    }

}
