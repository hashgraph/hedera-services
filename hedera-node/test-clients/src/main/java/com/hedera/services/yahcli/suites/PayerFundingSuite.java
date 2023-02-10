/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.yahcli.suites;

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.PAYER;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.checkBoxed;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PayerFundingSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(PayerFundingSuite.class);

    private final long guaranteedBalance;
    private final Map<String, String> specConfig;

    public PayerFundingSuite(long guaranteedBalance, Map<String, String> specConfig) {
        this.guaranteedBalance = guaranteedBalance;
        this.specConfig = specConfig;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    fundPayer(),
                });
    }

    private HapiSpec fundPayer() {
        return customHapiSpec("FundPayer")
                .withProperties(specConfig)
                .given(
                        withOpContext(
                                (spec, opLog) -> {
                                    var subOp = getAccountBalance(PAYER);
                                    allRunFor(spec, subOp);
                                    var balance =
                                            subOp.getResponse()
                                                    .getCryptogetAccountBalance()
                                                    .getBalance();
                                    if (balance < guaranteedBalance) {
                                        var funding =
                                                cryptoTransfer(
                                                        tinyBarsFromTo(
                                                                DEFAULT_PAYER,
                                                                PAYER,
                                                                guaranteedBalance - balance));
                                        allRunFor(spec, funding);
                                    }
                                }))
                .when()
                .then(logIt(checkBoxed("Payer has at least " + guaranteedBalance + " tℏ")));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
