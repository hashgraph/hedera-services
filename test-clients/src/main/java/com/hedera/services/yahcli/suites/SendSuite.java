/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SendSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(SendSuite.class);

    private final Map<String, String> specConfig;
    private final String memo;
    private final String beneficiary;
    @Nullable private final String denomination;
    private final long unitsToSend;

    public SendSuite(
            final Map<String, String> specConfig,
            final String beneficiary,
            final long unitsToSend,
            final String memo,
            @Nullable final String denomination) {
        this.memo = memo;
        this.specConfig = specConfig;
        this.beneficiary = Utils.extractAccount(beneficiary);
        this.unitsToSend = unitsToSend;
        this.denomination = denomination;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(doSend());
    }

    private HapiSpec doSend() {
        return HapiSpec.customHapiSpec("DoSend")
                .withProperties(specConfig)
                .given()
                .when()
                .then(
                        denomination == null
                                ? cryptoTransfer(
                                                tinyBarsFromTo(
                                                        DEFAULT_PAYER, beneficiary, unitsToSend))
                                        .memo(memo)
                                        .signedBy(DEFAULT_PAYER)
                                : cryptoTransfer(
                                                moving(unitsToSend, denomination)
                                                        .between(DEFAULT_PAYER, beneficiary))
                                        .memo(memo)
                                        .signedBy(DEFAULT_PAYER));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
