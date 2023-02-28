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

package com.hedera.services.yahcli.suites;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeAbort;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FreezeHelperSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(FreezeHelperSuite.class);

    private final Instant freezeStartTime;
    private final boolean isAbort;

    private final Map<String, String> specConfig;

    public FreezeHelperSuite(
            final Map<String, String> specConfig, final Instant freezeStartTime, final boolean isAbort) {
        this.isAbort = isAbort;
        this.specConfig = specConfig;
        this.freezeStartTime = freezeStartTime;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {doFreeze()});
    }

    private HapiSpec doFreeze() {
        return HapiSpec.customHapiSpec("DoFreeze")
                .withProperties(specConfig)
                .given()
                .when()
                .then(requestedFreezeOp());
    }

    private HapiSpecOperation requestedFreezeOp() {
        return isAbort
                ? freezeAbort().noLogging().yahcliLogging()
                : freezeOnly().startingAt(freezeStartTime).noLogging();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
