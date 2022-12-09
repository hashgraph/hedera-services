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
package com.hedera.services.bdd.suites.issues;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Issue2150Spec extends HapiSuite {
    private static final Logger log = LogManager.getLogger(Issue2150Spec.class);

    public static void main(String... args) {
        new Issue2150Spec().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    multiKeyNonPayerEntityVerifiedAsync(),
                });
    }

    private HapiSpec multiKeyNonPayerEntityVerifiedAsync() {
        KeyShape LARGE_THRESH_SHAPE = KeyShape.threshOf(1, 10);
        SigControl firstOnly =
                LARGE_THRESH_SHAPE.signedWith(
                        sigs(ON, OFF, OFF, OFF, OFF, OFF, OFF, OFF, OFF, OFF));

        return defaultHapiSpec("MultiKeyNonPayerEntityVerifiedAsync")
                .given(
                        newKeyNamed("payerKey").shape(LARGE_THRESH_SHAPE),
                        newKeyNamed("receiverKey").shape(LARGE_THRESH_SHAPE),
                        cryptoCreate("payer").keyShape(LARGE_THRESH_SHAPE),
                        cryptoCreate("receiver")
                                .keyShape(LARGE_THRESH_SHAPE)
                                .receiverSigRequired(true))
                .when()
                .then(
                        cryptoTransfer(tinyBarsFromTo("payer", "receiver", 1L))
                                .sigControl(
                                        forKey("payer", firstOnly), forKey("receiver", firstOnly)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
