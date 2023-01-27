package com.hedera.services.bdd.suites.regression;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.regression.factories.AccountCompletionFuzzingFactory.accountCompletionFuzzingWith;
import static com.hedera.services.bdd.suites.regression.factories.AccountCompletionFuzzingFactory.accountCreation;

public class HollowAccountCompletionFuzzing extends HapiSuite {
    private static final Logger log = LogManager.getLogger(HollowAccountCompletionFuzzing.class);

    private static final String PROPERTIES = "hollow-account-fuzzing.properties";

    public static void main(String... args) {
        new HollowAccountCompletionFuzzing().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(hollowAccountCompletionFuzzing());
    }

    private HapiSpec hollowAccountCompletionFuzzing() {
        return defaultHapiSpec("HollowAccountCompletionFuzzing")
                .given(accountCreation())
                .when()
                .then(runWithProvider(accountCompletionFuzzingWith(PROPERTIES)).lasting(10L, TimeUnit.SECONDS));
    }

    /*.given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR),
                        cryptoCreate(CRYPTO_TRANSFER_RECEIVER).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    assert addressBytes.length > 0;
                                    final var evmAddressBytes = ByteString.copyFrom(addressBytes);
                                    final var op =
                                            cryptoCreate(ACCOUNT)
                                                    .evmAddress(evmAddressBytes)
                                                    .balance(100 * ONE_HBAR)
                                                    .via("createTxn");

                                    final HapiGetTxnRecord hapiGetTxnRecord =
                                            getTxnRecord("createTxn").andAllChildRecords().logged();

                                    allRunFor(spec, op, hapiGetTxnRecord);

                                    final AccountID newAccountID =
                                            hapiGetTxnRecord
                                                    .getResponseRecord()
                                                    .getReceipt()
                                                    .getAccountID();
                                    spec.registry()
                                            .saveAccountId(SECP_256K1_SOURCE_KEY, newAccountID);

                                    final var op2 =
                                            cryptoTransfer(
                                                            tinyBarsFromTo(
                                                                    LAZY_CREATE_SPONSOR,
                                                                    CRYPTO_TRANSFER_RECEIVER,
                                                                    ONE_HUNDRED_HBARS))
                                                    .payingWith(SECP_256K1_SOURCE_KEY)
                                                    .sigMapPrefixes(
                                                            uniqueWithFullPrefixesFor(
                                                                    SECP_256K1_SOURCE_KEY))
                                                    .hasKnownStatus(SUCCESS)
                                                    .via(TRANSFER_TXN_2);

                                    var hapiGetAccountInfo =
                                            getAccountInfo(ACCOUNT)
                                                    .logged()
                                                    .has(
                                                            accountWith()
                                                                    .evmAddress(evmAddressBytes)
                                                                    .key(SECP_256K1_SOURCE_KEY)
                                                                    .noAlias());

                                    allRunFor(spec, op2, hapiGetAccountInfo);
                                }))
                .then();
     */
    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}