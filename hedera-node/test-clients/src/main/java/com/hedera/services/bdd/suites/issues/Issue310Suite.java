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

package com.hedera.services.bdd.suites.issues;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.crypto.CryptoCreateSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Issue310Suite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CryptoCreateSuite.class);

    public static void main(String... args) {
        new Issue310Suite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                duplicatedTxnsSameTypeDetected(),
                duplicatedTxnsDifferentTypesDetected(),
                duplicatedTxnsSameTypeDifferntNodesDetected(),
                duplicatedTxnsDiffrentTypesDifferentNodesDetected());
    }

    private HapiSpec duplicatedTxnsSameTypeDetected() {
        long initialBalance = 10_000L;

        return defaultHapiSpec("duplicatedTxnsSameTypeDetected")
                .given(
                        cryptoCreate("acct1").balance(initialBalance).logged().via("txnId1"),
                        UtilVerbs.sleepFor(1000),
                        cryptoCreate("acctWithDuplicateTxnId")
                                .balance(initialBalance)
                                .logged()
                                .txnId("txnId1")
                                .hasPrecheck(DUPLICATE_TRANSACTION))
                .when()
                .then(getTxnRecord("txnId1").logged());
    }

    private HapiSpec duplicatedTxnsDifferentTypesDetected() {
        return defaultHapiSpec("duplicatedTxnsDifferentTypesDetected")
                .given(
                        cryptoCreate("acct2").via("txnId2"),
                        newKeyNamed("key1"),
                        createTopic("topic2").submitKeyName("key1"))
                .when(submitMessageTo("topic2")
                        .message("Hello world")
                        .payingWith("acct2")
                        .txnId("txnId2")
                        .hasPrecheck(DUPLICATE_TRANSACTION))
                .then(getTxnRecord("txnId2").logged());
    }

    private HapiSpec duplicatedTxnsSameTypeDifferntNodesDetected() {

        return defaultHapiSpec("duplicatedTxnsSameTypeDifferntNodesDetected")
                .given(
                        cryptoCreate("acct3").setNode("0.0.3").via("txnId1"),
                        UtilVerbs.sleepFor(1000),
                        cryptoCreate("acctWithDuplicateTxnId")
                                .setNode("0.0.5")
                                .txnId("txnId1")
                                .hasPrecheck(DUPLICATE_TRANSACTION))
                .when()
                .then(getTxnRecord("txnId1").logged());
    }

    private HapiSpec duplicatedTxnsDiffrentTypesDifferentNodesDetected() {
        return defaultHapiSpec("duplicatedTxnsDiffrentTypesDifferentNodesDetected")
                .given(
                        cryptoCreate("acct4").via("txnId4").setNode("0.0.3"),
                        newKeyNamed("key2"),
                        createTopic("topic2").setNode("0.0.5").submitKeyName("key2"))
                .when(submitMessageTo("topic2")
                        .message("Hello world")
                        .payingWith("acct4")
                        .txnId("txnId4")
                        .hasPrecheck(DUPLICATE_TRANSACTION))
                .then(getTxnRecord("txnId4").logged());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
