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

import static com.hedera.services.yahcli.suites.Utils.extractAccount;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class RekeySuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(RekeySuite.class);

    private final String account;
    private final String replKeyLoc;
    private final String replTarget;
    private final boolean genNewKey;
    private final Map<String, String> specConfig;

    public RekeySuite(
            Map<String, String> specConfig, String account, String replKeyLoc, boolean genNewKey, String replTarget) {
        this.specConfig = specConfig;
        this.replKeyLoc = replKeyLoc;
        this.genNewKey = genNewKey;
        this.replTarget = replTarget;
        this.account = extractAccount(account);
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(rekey());
    }

    final Stream<DynamicTest> rekey() {
        final var replKey = "replKey";
        final var newKeyLoc = replTarget.endsWith(".pem") ? replTarget : replTarget.replace(".pem", ".words");
        final var newKeyPass = TxnUtils.randomAlphaNumeric(12);

        return HapiSpec.customHapiSpec("rekey" + account)
                .withProperties(specConfig)
                .given(
                        genNewKey
                                ? UtilVerbs.newKeyNamed(replKey)
                                        .shape(SigControl.ED25519_ON)
                                        .exportingTo(newKeyLoc, newKeyPass)
                                        .yahcliLogged()
                                : UtilVerbs.keyFromFile(replKey, replKeyLoc)
                                        .exportingTo(newKeyLoc, newKeyPass)
                                        .yahcliLogged())
                .when(TxnVerbs.cryptoUpdate(account)
                        .signedBy(HapiSuite.DEFAULT_PAYER, replKey)
                        .key(replKey)
                        .noLogging()
                        .yahcliLogging())
                .then(UtilVerbs.withOpContext((spec, opLog) -> {
                    if (replTarget.endsWith(".words")) {
                        new File(replTarget).delete();
                    }
                }));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
