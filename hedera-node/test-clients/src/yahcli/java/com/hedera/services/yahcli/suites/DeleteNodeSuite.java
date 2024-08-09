/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeDelete;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.noOp;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class DeleteNodeSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(DeleteNodeSuite.class);

    private final Map<String, String> specConfig;
    private final long nodeId;

    @Nullable
    private final String adminKeyLoc;

    public DeleteNodeSuite(
            @NonNull final Map<String, String> specConfig, final long nodeId, @Nullable final String adminKeyLoc) {
        this.specConfig = specConfig;
        this.nodeId = nodeId;
        this.adminKeyLoc = adminKeyLoc;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(doDelete());
    }

    final Stream<DynamicTest> doDelete() {
        final var adminKey = "adminKey";
        return HapiSpec.customHapiSpec("DeleteNode")
                .withProperties(specConfig)
                .given(adminKeyLoc == null ? noOp() : keyFromFile(adminKey, adminKeyLoc))
                .when()
                .then(nodeDelete("" + nodeId).signedBy(availableSigners()));
    }

    private String[] availableSigners() {
        if (adminKeyLoc == null) {
            return new String[] {DEFAULT_PAYER};
        } else {
            return new String[] {DEFAULT_PAYER, "adminKey"};
        }
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
