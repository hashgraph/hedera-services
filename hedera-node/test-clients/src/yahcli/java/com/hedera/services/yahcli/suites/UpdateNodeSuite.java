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

import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class UpdateNodeSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(UpdateNodeSuite.class);

    private final Map<String, String> specConfig;
    private final long nodeId;

    public UpdateNodeSuite(@NonNull final Map<String, String> specConfig, final long nodeId) {
        this.specConfig = requireNonNull(specConfig);
        this.nodeId = nodeId;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(doUpdate());
    }

    final Stream<DynamicTest> doUpdate() {
        return HapiSpec.customHapiSpec("UpdateNode")
                .withProperties(specConfig)
                .given()
                .when()
                .then();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
