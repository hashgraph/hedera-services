package com.hedera.services.bdd.suites.regression;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.OpProvider.UNIQUE_PAYER_ACCOUNT;
import static com.hedera.services.bdd.spec.infrastructure.OpProvider.UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.suites.regression.factories.IdFuzzingProviderFactory.idFuzzingWith;

public class AddressAliasIdFuzzing extends HapiSuite {
    private static final Logger log = LogManager.getLogger(AddressAliasIdFuzzing.class);

    private static final String PROPERTIES = "id-fuzzing.properties";

    public static void main(String... args) {
        new AddressAliasIdFuzzing().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(addressAliasIdFuzzing());
    }

    private HapiSpec addressAliasIdFuzzing() {
        return defaultHapiSpec("AddressAliasIdFuzzing")
                .given(
                        cryptoCreate(UNIQUE_PAYER_ACCOUNT)
                                .balance(UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE)
                                .withRecharging()
                ).when().then(
                        runWithProvider(idFuzzingWith(PROPERTIES))
                                .lasting(60L, TimeUnit.SECONDS));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
