package com.hedera.services.bdd.suites.fees;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenPause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnpause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdateNfts;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hederahashgraph.api.proto.java.TokenPauseStatus.Paused;
import static com.hederahashgraph.api.proto.java.TokenPauseStatus.Unpaused;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class TokenServiceFeesSuite {
    private static final double ALLOWED_DIFFERENCE_PERCENTAGE = 0.01;
    private static String TOKEN_TREASURY = "treasury";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungible";
    private static final String SUPPLY_KEY = "supplyKey";
    private static final String METADATA_KEY = "metadataKey";
    private static final String ADMIN_KEY = "adminKey";
    private static final String PAUSE_KEY = "pauseKey";
    private static final String TREASURE_KEY = "treasureKey";
    private static final String FREEZE_KEY = "freezeKey";
    private static final String KYC_KEY = "kycKey";

    private static final String UNFREEZE = "unfreeze";


    private static final String CIVILIAN_ACCT = "civilian";
    private static final String UNIQUE_TOKEN = "nftType";
    private static final String BASE_TXN = "baseTxn";


    private static final String WIPE_KEY = "wipeKey";
    private static final String NFT_TEST_METADATA = " test metadata";
    private static final String FUNGIBLE_COMMON_TOKEN = "fungibleCommonToken";

    private static final double EXPECTED_NFT_WIPE_PRICE_USD = 0.001;
    private static final double EXPECTED_FREEZE_PRICE_USD = 0.001;
    private static final double EXPECTED_UNFREEZE_PRICE_USD = 0.001;


    @HapiTest
    final Stream<DynamicTest> baseNftFreezeUnfreezeChargedAsExpected() {
        return defaultHapiSpec("baseNftFreezeUnfreezeChargedAsExpected")
                .given(
                        newKeyNamed(TREASURE_KEY),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(KYC_KEY),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS).key(TREASURE_KEY),
                        cryptoCreate(CIVILIAN_ACCT),
                        tokenCreate(UNIQUE_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0L)
                                .adminKey(ADMIN_KEY)
                                .freezeKey(TOKEN_TREASURY)
                                .kycKey(KYC_KEY)
                                .freezeDefault(false)
                                .treasury(TOKEN_TREASURY)
                                .payingWith(TOKEN_TREASURY)
                                .supplyKey(ADMIN_KEY)
                                .via(BASE_TXN),
                        tokenAssociate(CIVILIAN_ACCT, UNIQUE_TOKEN))
                .when(
                        tokenFreeze(UNIQUE_TOKEN, CIVILIAN_ACCT)
                                .blankMemo()
                                .signedBy(TOKEN_TREASURY)
                                .payingWith(TOKEN_TREASURY)
                                .via("freeze"),
                        tokenUnfreeze(UNIQUE_TOKEN, CIVILIAN_ACCT)
                                .blankMemo()
                                .payingWith(TOKEN_TREASURY)
                                .signedBy(TOKEN_TREASURY)
                                .via(UNFREEZE))
                .then(
                        validateChargedUsdWithin("freeze", EXPECTED_FREEZE_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE),
                        validateChargedUsdWithin(UNFREEZE, EXPECTED_UNFREEZE_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE));
    }

    @HapiTest
    final Stream<DynamicTest> baseCommonFreezeUnfreezeChargedAsExpected() {
        return defaultHapiSpec("baseCommonFreezeUnfreezeChargedAsExpected")
                .given(
                        newKeyNamed(TREASURE_KEY),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(WIPE_KEY),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS).key(TREASURE_KEY),
                        cryptoCreate(CIVILIAN_ACCT),
                        tokenCreate(FUNGIBLE_COMMON_TOKEN)
                                .adminKey(ADMIN_KEY)
                                .freezeKey(TOKEN_TREASURY)
                                .wipeKey(WIPE_KEY)
                                .supplyKey(SUPPLY_KEY)
                                .freezeDefault(false)
                                .treasury(TOKEN_TREASURY)
                                .payingWith(TOKEN_TREASURY),
                        tokenAssociate(CIVILIAN_ACCT, FUNGIBLE_COMMON_TOKEN))
                .when(
                        tokenFreeze(FUNGIBLE_COMMON_TOKEN, CIVILIAN_ACCT)
                                .blankMemo()
                                .signedBy(TOKEN_TREASURY)
                                .payingWith(TOKEN_TREASURY)
                                .via("freeze"),
                        tokenUnfreeze(FUNGIBLE_COMMON_TOKEN, CIVILIAN_ACCT)
                                .blankMemo()
                                .payingWith(TOKEN_TREASURY)
                                .signedBy(TOKEN_TREASURY)
                                .via(UNFREEZE))
                .then(
                        validateChargedUsdWithin("freeze", EXPECTED_FREEZE_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE),
                        validateChargedUsdWithin(UNFREEZE, EXPECTED_UNFREEZE_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE));
    }

    @HapiTest
    final Stream<DynamicTest> basePauseAndUnpauseHaveExpectedPrices() {
        final var expectedBaseFee = 0.001;
        final var token = "token";
        final var tokenPauseTransaction = "tokenPauseTxn";
        final var tokenUnpauseTransaction = "tokenUnpauseTxn";
        final var civilian = "NonExemptPayer";

        return defaultHapiSpec("BasePauseAndUnpauseHaveExpectedPrices")
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        newKeyNamed(PAUSE_KEY),
                        cryptoCreate(civilian).key(PAUSE_KEY))
                .when(
                        tokenCreate(token)
                                .pauseKey(PAUSE_KEY)
                                .treasury(TOKEN_TREASURY)
                                .payingWith(civilian),
                        tokenPause(token).blankMemo().payingWith(civilian).via(tokenPauseTransaction),
                        getTokenInfo(token).hasPauseStatus(Paused),
                        tokenUnpause(token).blankMemo().payingWith(civilian).via(tokenUnpauseTransaction),
                        getTokenInfo(token).hasPauseStatus(Unpaused))
                .then(
                        validateChargedUsd(tokenPauseTransaction, expectedBaseFee),
                        validateChargedUsd(tokenUnpauseTransaction, expectedBaseFee));
    }

    @HapiTest
    final Stream<DynamicTest> baseNftWipeOperationIsChargedExpectedFee() {
        return defaultHapiSpec("BaseUniqueWipeOperationIsChargedExpectedFee")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(WIPE_KEY),
                        cryptoCreate(CIVILIAN_ACCT).key(WIPE_KEY),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS).key(WIPE_KEY),
                        tokenCreate(UNIQUE_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0L)
                                .supplyKey(SUPPLY_KEY)
                                .wipeKey(WIPE_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(CIVILIAN_ACCT, UNIQUE_TOKEN),
                        mintToken(UNIQUE_TOKEN, List.of(ByteString.copyFromUtf8("token_to_wipe"))),
                        cryptoTransfer(movingUnique(UNIQUE_TOKEN, 1L).between(TOKEN_TREASURY, CIVILIAN_ACCT)))
                .when(wipeTokenAccount(UNIQUE_TOKEN, CIVILIAN_ACCT, List.of(1L))
                        .payingWith(TOKEN_TREASURY)
                        .fee(ONE_HBAR)
                        .blankMemo()
                        .via(BASE_TXN))
                .then(validateChargedUsdWithin(BASE_TXN, EXPECTED_NFT_WIPE_PRICE_USD, 0.01));
    }


    @HapiTest
    final Stream<DynamicTest> updateSingleNftFeeChargedAsExpected() {
        final var expectedNftUpdatePriceUsd = 0.001;
        final var nftUpdateTxn = "nftUpdateTxn";

        return hapiTest(
                newKeyNamed(SUPPLY_KEY),
                newKeyNamed(WIPE_KEY),
                newKeyNamed(METADATA_KEY),
                cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                tokenCreate(NON_FUNGIBLE_TOKEN)
                        .supplyType(TokenSupplyType.FINITE)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .treasury(TOKEN_TREASURY)
                        .maxSupply(12L)
                        .wipeKey(WIPE_KEY)
                        .supplyKey(SUPPLY_KEY)
                        .metadataKey(METADATA_KEY)
                        .initialSupply(0L),
                mintToken(
                        NON_FUNGIBLE_TOKEN,
                        List.of(
                                copyFromUtf8("a"),
                                copyFromUtf8("b"),
                                copyFromUtf8("c"),
                                copyFromUtf8("d"),
                                copyFromUtf8("e"),
                                copyFromUtf8("f"),
                                copyFromUtf8("g"))),
                tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(7L))
                        .signedBy(TOKEN_TREASURY, METADATA_KEY)
                        .payingWith(TOKEN_TREASURY)
                        .fee(10 * ONE_HBAR)
                        .via(nftUpdateTxn),
                validateChargedUsdWithin(nftUpdateTxn, expectedNftUpdatePriceUsd, 0.01));
    }

    @HapiTest
    final Stream<DynamicTest> updateMultipleNftsFeeChargedAsExpected() {
        final var expectedNftUpdatePriceUsd = 0.005;
        final var nftUpdateTxn = "nftUpdateTxn";

        return hapiTest(
                newKeyNamed(SUPPLY_KEY),
                newKeyNamed(WIPE_KEY),
                newKeyNamed(METADATA_KEY),
                cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                tokenCreate(NON_FUNGIBLE_TOKEN)
                        .supplyType(TokenSupplyType.FINITE)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .treasury(TOKEN_TREASURY)
                        .maxSupply(12L)
                        .wipeKey(WIPE_KEY)
                        .supplyKey(SUPPLY_KEY)
                        .metadataKey(METADATA_KEY)
                        .initialSupply(0L),
                mintToken(
                        NON_FUNGIBLE_TOKEN,
                        List.of(
                                copyFromUtf8("a"),
                                copyFromUtf8("b"),
                                copyFromUtf8("c"),
                                copyFromUtf8("d"),
                                copyFromUtf8("e"),
                                copyFromUtf8("f"),
                                copyFromUtf8("g"))),
                tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(1L, 2L, 3L, 4L, 5L))
                        .signedBy(TOKEN_TREASURY, METADATA_KEY)
                        .payingWith(TOKEN_TREASURY)
                        .fee(10 * ONE_HBAR)
                        .via(nftUpdateTxn),
                validateChargedUsdWithin(nftUpdateTxn, expectedNftUpdatePriceUsd, 0.01));
    }
}
