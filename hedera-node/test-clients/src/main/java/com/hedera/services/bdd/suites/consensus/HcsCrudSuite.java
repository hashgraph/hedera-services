/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.consensus;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.deleteTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.time.Instant;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HcsCrudSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(HcsCrudSuite.class);

    public static void main(String... args) {
        new HcsCrudSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(basicCrud());
    }

    private HapiSpec basicCrud() {
        final var oldMemo = "old";
        final var newMemo = "new";
        final var firstKey = "firstKey";
        final var secondKey = "secondKey";
        final var topicToUpdate = "topicToUpdate";
        final var topicToDelete = "topicToDelete";

        return defaultHapiSpec("BasicCrud")
                .given(
                        newKeyNamed(firstKey),
                        newKeyNamed(secondKey),
                        // Create two topics, send some messages to both, delete one
                        createTopic(topicToUpdate)
                                .memo(oldMemo)
                                .adminKeyName(firstKey)
                                .submitKeyName(firstKey),
                        createTopic(topicToDelete).adminKeyName(firstKey))
                .when(
                        submitMessageTo(topicToUpdate).message("Hello"),
                        submitMessageTo(topicToDelete).message("World"),
                        submitMessageTo(topicToUpdate).message("!"))
                .then(
                        updateTopic(topicToUpdate).submitKey(secondKey).topicMemo(newMemo),
                        deleteTopic(topicToDelete),
                        // Trigger snapshot of final state for replay assets
                        freezeOnly()
                                .payingWith(GENESIS)
                                .startingAt(Instant.now().plusSeconds(10)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
