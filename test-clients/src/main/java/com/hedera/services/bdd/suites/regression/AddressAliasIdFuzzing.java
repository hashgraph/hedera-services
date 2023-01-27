/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.regression;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.OpProvider.UNIQUE_PAYER_ACCOUNT;
import static com.hedera.services.bdd.spec.infrastructure.OpProvider.UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.suites.regression.factories.IdFuzzingProviderFactory.idFuzzingWith;
import static com.hedera.services.bdd.suites.regression.factories.IdFuzzingProviderFactory.idTransferExperimentsWith;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * We want to make this suite exercise all forms of identity a Hedera account may have, under all
 * possible circumstances. (This could take us a while to do.)
 *
 * <p>See <a href="https://github.com/hashgraph/hedera-services/issues/4565">#4565</a> for details.
 */
public class AddressAliasIdFuzzing extends HapiSuite {
    private static final Logger log = LogManager.getLogger(AddressAliasIdFuzzing.class);

    private static final String PROPERTIES = "id-fuzzing.properties";

    public static void main(String... args) {
        new AddressAliasIdFuzzing().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(transferExperimentsFuzzing());
    }

    private HapiSpec addressAliasIdFuzzing() {
        return defaultHapiSpec("AddressAliasIdFuzzing")
                .given(
                        cryptoCreate(UNIQUE_PAYER_ACCOUNT)
                                .balance(UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE)
                                .withRecharging())
                .when()
                .then(runWithProvider(idFuzzingWith(PROPERTIES)).lasting(10L, TimeUnit.SECONDS));
    }

    private HapiSpec transferExperimentsFuzzing() {
        return defaultHapiSpec("TransferExperimentsFuzzing")
                .given(
                        cryptoCreate(UNIQUE_PAYER_ACCOUNT)
                                .balance(UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE)
                                .withRecharging())
                .when()
                .then(
                        runWithProvider(idTransferExperimentsWith(PROPERTIES))
                                .lasting(10L, TimeUnit.SECONDS));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
