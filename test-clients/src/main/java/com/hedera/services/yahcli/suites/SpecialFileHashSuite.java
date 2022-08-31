/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SpecialFileHashSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(SpecialFileHashSuite.class);

    private final String specialFile;
    private final Map<String, String> specConfig;

    public SpecialFileHashSuite(Map<String, String> specConfig, String specialFile) {
        this.specConfig = specConfig;
        this.specialFile = specialFile;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(getSpecialFileHash());
    }

    private HapiApiSpec getSpecialFileHash() {
        long target = Utils.rationalized(specialFile);

        return HapiApiSpec.customHapiSpec("GetSpecialFileHash")
                .withProperties(specConfig)
                .given()
                .when()
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var lookup = getFileInfo("0.0." + target);
                                    allRunFor(spec, lookup);
                                    final var synthMemo =
                                            lookup.getResponse()
                                                    .getFileGetInfo()
                                                    .getFileInfo()
                                                    .getMemo();
                                    COMMON_MESSAGES.info(
                                            "The SHA-384 hash of the "
                                                    + specialFile
                                                    + " is:\n"
                                                    + synthMemo);
                                }));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
