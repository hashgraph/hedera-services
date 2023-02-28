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

package com.hedera.services.bdd.suites.misc;

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.suites.misc.RekeySavedStateTreasury.newTreasuryPassphrase;
import static com.hedera.services.bdd.suites.misc.RekeySavedStateTreasury.newTreasuryPemLoc;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Given a network with a "re-keyed" treasury account, we want to use this the new treasury account
 * to freeze our network and generate a signed state.
 */
public class FreezeRekeyedState extends HapiSuite {
    private static final Logger log = LogManager.getLogger(FreezeRekeyedState.class);

    public static void main(String... args) {
        new FreezeRekeyedState().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            freezeWithNewTreasuryKey(),
        });
    }

    private HapiSpec freezeWithNewTreasuryKey() {
        return customHapiSpec("FreezeWithNewTreasuryKey")
                .withProperties(Map.of(
                        "nodes",
                        "localhost",
                        "default.payer",
                        "0.0.2",
                        "default.payer.pemKeyLoc",
                        newTreasuryPemLoc,
                        "default.payer.pemKeyPassphrase",
                        newTreasuryPassphrase))
                .given()
                .when()
                .then(freezeOnly().startingIn(60).seconds());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
