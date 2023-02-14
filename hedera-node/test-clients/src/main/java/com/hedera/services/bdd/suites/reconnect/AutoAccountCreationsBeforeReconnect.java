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
package com.hedera.services.bdd.suites.reconnect;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithAlias;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AutoAccountCreationsBeforeReconnect extends HapiSuite {
    private static final Logger log =
            LogManager.getLogger(AutoAccountCreationsBeforeReconnect.class);

    public static final int TOTAL_ACCOUNTS = 10;

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(createAccountsUsingAlias());
    }

    public static void main(String... args) {
        new AutoAccountCreationsBeforeReconnect().runSuiteSync();
    }

    private HapiSpec createAccountsUsingAlias() {
        return defaultHapiSpec("createAccountsUsingAlias")
                .given()
                .when()
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    List<HapiSpecOperation> ops = new ArrayList<>();
                                    for (int i = 0; i < TOTAL_ACCOUNTS; i++) {
                                        var alias = "alias" + i;
                                        ops.add(newKeyNamed(alias));
                                        ops.add(
                                                cryptoTransfer(
                                                        tinyBarsFromToWithAlias(
                                                                DEFAULT_PAYER, alias, ONE_HBAR)));
                                    }
                                    allRunFor(spec, ops);
                                }));
    }
}
