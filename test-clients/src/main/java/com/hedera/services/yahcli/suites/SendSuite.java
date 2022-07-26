/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.yahcli.suites;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SendSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(SendSuite.class);

    private final Map<String, String> specConfig;
    private final String memo;
    private final String beneficiary;
    private final long tinybarsToSend;

    public SendSuite(
            final Map<String, String> specConfig,
            final String beneficiary,
            final long tinybarsToSend,
            final String memo) {
        this.memo = memo;
        this.specConfig = specConfig;
        this.beneficiary = Utils.extractAccount(beneficiary);
        this.tinybarsToSend = tinybarsToSend;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(doSend());
    }

    private HapiApiSpec doSend() {
        return HapiApiSpec.customHapiSpec("DoSend")
                .withProperties(specConfig)
                .given()
                .when()
                .then(
                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, beneficiary, tinybarsToSend))
                                .memo(memo)
                                .signedBy(DEFAULT_PAYER));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
