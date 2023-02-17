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

package com.hedera.services.bdd.suites.compose;

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LocalNetworkCheck extends HapiSuite {
    private static final Logger log = LogManager.getLogger(LocalNetworkCheck.class);

    public static void main(String... args) {
        new LocalNetworkCheck().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            balancesChangeOnTransfer(),
        });
    }

    private HapiSpec balancesChangeOnTransfer() {
        return customHapiSpec("BalancesChangeOnTransfer")
                .withProperties(Map.of("nodes", "127.0.0.1:50213:0.0.3,127.0.0.1:50214:0.0.4,127.0.0.1:50215:0.0.5"))
                .given(
                        cryptoCreate("sponsor").setNode("0.0.3"),
                        cryptoCreate("beneficiary").setNode("0.0.4"),
                        balanceSnapshot("sponsorBefore", "sponsor"),
                        balanceSnapshot("beneficiaryBefore", "beneficiary"))
                .when(cryptoTransfer(tinyBarsFromTo("sponsor", "beneficiary", 1L))
                        .payingWith(GENESIS)
                        .memo("Hello World!")
                        .setNode("0.0.5"))
                .then(
                        getAccountBalance("sponsor").hasTinyBars(changeFromSnapshot("sponsorBefore", -1L)),
                        getAccountBalance("beneficiary").hasTinyBars(changeFromSnapshot("beneficiaryBefore", +1L)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
