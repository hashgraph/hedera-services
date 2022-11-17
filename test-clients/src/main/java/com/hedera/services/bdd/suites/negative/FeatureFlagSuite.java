package com.hedera.services.bdd.suites.negative;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiApiSpec.onlyDefaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.hapiPrng;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithAlias;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOf;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.A_TOKEN;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.NFT_CREATE;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.NFT_INFINITE_SUPPLY_TOKEN;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.TOKEN_A_CREATE;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.VALID_ALIAS;
import static com.hedera.services.bdd.suites.file.FileUpdateSuite.CIVILIAN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.TRANSFER_TXN;
import static com.hedera.services.bdd.suites.util.UtilPrngSuite.BOB;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

import com.hedera.services.bdd.spec.HapiApiSpec;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FeatureFlagSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(FeatureFlagSuite.class);

    public static void main(String... args) {
        new FeatureFlagSuite().runSuiteSync();
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                disablesAllFeatureFlagsAndConfirmsNotSupported(),
                enablesAllFeatureFlagsAndDisableThrottlesForFurtherCiTesting()
        );
    }

    private HapiApiSpec disablesAllFeatureFlagsAndConfirmsNotSupported() {
        return defaultHapiSpec("DisablesAllFeatureFlagsAndConfirmsNotSupported")
                .given(overridingAllOf(FeatureFlags.FEATURE_FLAGS.allDisabled()))
                .when()
                .then(inParallel(
                        confirmAutoCreationNotSupported(),
                        confirmUtilPrngNotSupported(),
                        confirmTokenAutoCreationNotSupported()
                ));
    }

    private HapiApiSpec enablesAllFeatureFlagsAndDisableThrottlesForFurtherCiTesting() {
        return defaultHapiSpec("EnablesAllFeatureFlagsForFurtherCiTesting")
                .given(overridingAllOf(FeatureFlags.FEATURE_FLAGS.allEnabled()))
                .when()
                .then(overriding("contracts.throttle.throttleByGas", "false"));
    }

    private HapiSpecOperation confirmAutoCreationNotSupported() {
        final var aliasKey = "autoCreationKey";
        return UtilVerbs.blockingOrder(
                newKeyNamed(aliasKey),
                cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, aliasKey, ONE_HBAR))
                        .hasKnownStatus(NOT_SUPPORTED));
    }

    private HapiSpecOperation confirmUtilPrngNotSupported() {
        return UtilVerbs.blockingOrder(
                cryptoCreate(BOB).balance(ONE_HUNDRED_HBARS),
                hapiPrng().payingWith(BOB).via("baseTxn").blankMemo().logged(),
                getTxnRecord("baseTxn").hasNoPseudoRandomData(),
                hapiPrng(10).payingWith(BOB).via("plusRangeTxn").blankMemo().logged(),
                getTxnRecord("plusRangeTxn").hasNoPseudoRandomData());
    }

    private HapiSpecOperation confirmTokenAutoCreationNotSupported() {
        final var initialTokenSupply = 1000;
        final var fungibleTokenXfer = "fungibleTokenXfer";
        final var nftXfer = "nftXfer";

        return UtilVerbs.blockingOrder(
                newKeyNamed(VALID_ALIAS),
                newKeyNamed(MULTI_KEY),
                cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                tokenCreate(A_TOKEN)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .initialSupply(initialTokenSupply)
                        .treasury(TOKEN_TREASURY)
                        .via(TOKEN_A_CREATE),
                tokenCreate(NFT_INFINITE_SUPPLY_TOKEN)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .adminKey(MULTI_KEY)
                        .supplyKey(MULTI_KEY)
                        .supplyType(TokenSupplyType.INFINITE)
                        .initialSupply(0)
                        .treasury(TOKEN_TREASURY)
                        .via(NFT_CREATE),
                mintToken(
                        NFT_INFINITE_SUPPLY_TOKEN,
                        List.of(
                                ByteString.copyFromUtf8("a"),
                                ByteString.copyFromUtf8("b"))),
                cryptoCreate(CIVILIAN)
                        .balance(10 * ONE_HBAR)
                        .maxAutomaticTokenAssociations(2),
                tokenAssociate(CIVILIAN, NFT_INFINITE_SUPPLY_TOKEN),
                cryptoTransfer(
                        moving(100, A_TOKEN).between(TOKEN_TREASURY, CIVILIAN),
                        movingUnique(NFT_INFINITE_SUPPLY_TOKEN, 1L, 2L)
                                .between(TOKEN_TREASURY, CIVILIAN)),
                getAccountInfo(CIVILIAN).hasToken(relationshipWith(A_TOKEN).balance(100)),
                getAccountInfo(CIVILIAN)
                        .hasToken(relationshipWith(NFT_INFINITE_SUPPLY_TOKEN)),

                /* --- transfer token type to alias and expected to fail as the feature flag is off  --- */
                cryptoTransfer(moving(10, A_TOKEN).between(CIVILIAN, VALID_ALIAS))
                        .via(fungibleTokenXfer)
                        .payingWith(CIVILIAN)
                        .hasKnownStatus(NOT_SUPPORTED)
                        .logged(),
                cryptoTransfer(
                        movingUnique(NFT_INFINITE_SUPPLY_TOKEN, 1, 2)
                                .between(CIVILIAN, VALID_ALIAS))
                        .via(nftXfer)
                        .payingWith(CIVILIAN)
                        .hasKnownStatus(NOT_SUPPORTED)
                        .logged(),
                getTxnRecord(fungibleTokenXfer).andAllChildRecords().hasChildRecordCount(0),
                getTxnRecord(nftXfer).andAllChildRecords().hasNonStakingChildRecordCount(0),
                /* --- hbar auto creations should still pass */
                cryptoTransfer(tinyBarsFromToWithAlias(CIVILIAN, VALID_ALIAS, ONE_HBAR))
                        .payingWith(CIVILIAN)
                        .signedBy(CIVILIAN, VALID_ALIAS)
                        .via(TRANSFER_TXN),
                getTxnRecord(TRANSFER_TXN)
                        .andAllChildRecords()
                        .hasNonStakingChildRecordCount(1)
        );
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    private enum FeatureFlags {
        FEATURE_FLAGS;

        public Map<String, String> allEnabled() {
            return all("true");
        }

        public Map<String, String> allDisabled() {
            return all("false");
        }

        private Map<String, String> all(final String choice) {
            return Map.ofEntries(Arrays.asList(NAMES).stream()
                    .map(name -> Map.entry(name, choice))
                    .toArray(Map.Entry[]::new));
        }

        private static final String[] NAMES = {
                "autoCreation.enabled",
                // Not being tested
                "contracts.itemizeStorageFees",
                // Not being tested
                "contracts.precompile.htsEnableTokenCreate",
                // Not being tested
                "contracts.redirectTokenCalls",
                "contracts.throttle.throttleByGas",
                // Not being tested
                "hedera.allowances.isEnabled",
                // Behavior doesn't make sense, but is tested
                "utilPrng.isEnabled",
                "tokens.autoCreations.isEnabled",
                "lazyCreation.enabled",
                "cryptoCreateWithAlias.enabled",
                "contracts.allowAutoAssociations",
                "contracts.enforceCreationThrottle",
                "contracts.precompile.atomicCryptoTransfer.enabled",
                "scheduling.longTermEnabled",
        };
    }
}
