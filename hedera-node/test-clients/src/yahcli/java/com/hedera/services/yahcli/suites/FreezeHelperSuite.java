// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.yahcli.suites;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

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
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(doFreeze());
    }

    final Stream<DynamicTest> doFreeze() {
        return HapiSpec.customHapiSpec("DoFreeze")
                .withProperties(specConfig)
                .given()
                .when()
                .then(requestedFreezeOp());
    }

    private HapiSpecOperation requestedFreezeOp() {
        return isAbort
                ? UtilVerbs.freezeAbort().noLogging().yahcliLogging()
                : UtilVerbs.freezeOnly().startingAt(freezeStartTime).noLogging();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
