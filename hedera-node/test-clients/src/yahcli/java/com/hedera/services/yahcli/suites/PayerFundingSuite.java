/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.yahcli.commands.validation.ValidationCommand;
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
        return List.of(new HapiSpec[] {
            fundPayer(),
        });
    }

    private HapiSpec fundPayer() {
        return HapiSpec.customHapiSpec("FundPayer")
                .withProperties(specConfig)
                .given(UtilVerbs.withOpContext((spec, opLog) -> {
                    var subOp = QueryVerbs.getAccountBalance(ValidationCommand.PAYER);
                    CustomSpecAssert.allRunFor(spec, subOp);
                    var balance =
                            subOp.getResponse().getCryptogetAccountBalance().getBalance();
                    if (balance < guaranteedBalance) {
                        var funding = TxnVerbs.cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(
                                HapiSuite.DEFAULT_PAYER, ValidationCommand.PAYER, guaranteedBalance - balance));
                        CustomSpecAssert.allRunFor(spec, funding);
                    }
                }))
                .when()
                .then(UtilVerbs.logIt(ValidationCommand.checkBoxed("Payer has at least " + guaranteedBalance + " tℏ")));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
