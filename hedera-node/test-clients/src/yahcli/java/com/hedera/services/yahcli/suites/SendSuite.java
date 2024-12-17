/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiSuite;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class SendSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(SendSuite.class);

    private final Map<String, String> specConfig;
    private final String memo;
    private final String beneficiary;

    @Nullable
    private final String denomination;

    private final boolean schedule;
    private final long unitsToSend;

    public SendSuite(
            final Map<String, String> specConfig,
            final String beneficiary,
            final long unitsToSend,
            final String memo,
            @Nullable final String denomination,
            final boolean schedule) {
        this.memo = memo;
        this.specConfig = specConfig;
        this.beneficiary = Utils.extractAccount(beneficiary);
        this.unitsToSend = unitsToSend;
        this.denomination = denomination;
        this.schedule = schedule;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(doSend());
    }

    final Stream<DynamicTest> doSend() {
        var transfer = denomination == null
                ? (HapiTxnOp<?>) TxnVerbs.cryptoTransfer(
                                HapiCryptoTransfer.tinyBarsFromTo(HapiSuite.DEFAULT_PAYER, beneficiary, unitsToSend))
                        .memo(memo)
                        .signedBy(HapiSuite.DEFAULT_PAYER)
                : (HapiTxnOp<?>) TxnVerbs.cryptoTransfer(TokenMovement.moving(unitsToSend, denomination)
                                .between(HapiSuite.DEFAULT_PAYER, beneficiary))
                        .memo(memo)
                        .signedBy(HapiSuite.DEFAULT_PAYER);

        // flag that transferred as parameter to schedule a transaction or to execute right away
        if (schedule) {
            transfer = TxnVerbs.scheduleCreate("original", transfer).logged();
        }

        return HapiSpec.customHapiSpec("DoSend")
                .withProperties(specConfig)
                .given()
                .when()
                .then(transfer);
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
