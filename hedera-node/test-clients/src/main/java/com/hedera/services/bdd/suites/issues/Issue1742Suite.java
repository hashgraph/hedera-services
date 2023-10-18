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

package com.hedera.services.bdd.suites.issues;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class Issue1742Suite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(Issue1742Suite.class);

    public static void main(String... args) {
        new Issue1742Suite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(cryptoTransferListShowsOnlyFeesAfterIAB());
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @HapiTest
    public static HapiSpec cryptoTransferListShowsOnlyFeesAfterIAB() {
        final long PAYER_BALANCE = 1_000_000L;

        return defaultHapiSpec("CryptoTransferListShowsOnlyFeesAfterIAB")
                .given(cryptoCreate("payer").balance(PAYER_BALANCE))
                .when()
                .then(cryptoTransfer(tinyBarsFromTo("payer", GENESIS, PAYER_BALANCE))
                        .payingWith("payer")
                        .via("txn")
                        .hasPrecheck(INSUFFICIENT_PAYER_BALANCE));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
