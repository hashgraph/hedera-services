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
package com.hedera.services.bdd.suites.streaming;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.verifyRecordStreams;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RecordStreamValidation extends HapiSuite {
    private static final Logger log = LogManager.getLogger(RecordStreamValidation.class);

    private static final String PATH_TO_LOCAL_STREAMS = "../data/recordstreams";

    public static void main(String... args) {
        new RecordStreamValidation().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    recordStreamSanityChecks(),
                });
    }

    private HapiSpec recordStreamSanityChecks() {
        AtomicReference<String> pathToStreams = new AtomicReference<>(PATH_TO_LOCAL_STREAMS);

        return defaultHapiSpec("RecordStreamSanityChecks")
                .given(
                        withOpContext(
                                (spec, opLog) -> {
                                    HapiPropertySource ciProps = spec.setup().ciPropertiesMap();
                                    if (ciProps.has("recordStreamsDir")) {
                                        pathToStreams.set(ciProps.get("recordStreamsDir"));
                                    }
                                }))
                .when()
                .then(verifyRecordStreams(pathToStreams::get));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
