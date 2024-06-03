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

import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class SpecialFileHashSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(SpecialFileHashSuite.class);

    private final String specialFile;
    private final Map<String, String> specConfig;

    public SpecialFileHashSuite(Map<String, String> specConfig, String specialFile) {
        this.specConfig = specConfig;
        this.specialFile = specialFile;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(getSpecialFileHash());
    }

    final Stream<DynamicTest> getSpecialFileHash() {
        long target = Utils.rationalized(specialFile);

        return HapiSpec.customHapiSpec("GetSpecialFileHash")
                .withProperties(specConfig)
                .given()
                .when()
                .then(UtilVerbs.withOpContext((spec, opLog) -> {
                    final var lookup = QueryVerbs.getFileInfo("0.0." + target);
                    CustomSpecAssert.allRunFor(spec, lookup);
                    final var synthMemo =
                            lookup.getResponse().getFileGetInfo().getFileInfo().getMemo();
                    COMMON_MESSAGES.info("The SHA-384 hash of the " + specialFile + " is:\n" + synthMemo);
                }));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
