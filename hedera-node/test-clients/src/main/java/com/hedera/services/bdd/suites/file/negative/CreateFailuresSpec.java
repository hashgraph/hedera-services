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

package com.hedera.services.bdd.suites.file.negative;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.time.Instant;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class CreateFailuresSpec extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CreateFailuresSpec.class);

    public static void main(String... args) {
        new CreateFailuresSpec().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            //						handleRejectsMissingWacl(),
            precheckRejectsBadEffectiveAutoRenewPeriod(),
        });
    }

    @HapiTest
    private HapiSpec precheckRejectsBadEffectiveAutoRenewPeriod() {
        var now = Instant.now();
        System.out.println(now.getEpochSecond());

        return defaultHapiSpec("precheckRejectsBadEffectiveAutoRenewPeriod")
                .given()
                .when()
                .then(fileCreate("notHere")
                        .lifetime(-60L)
                        .hasPrecheck(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
