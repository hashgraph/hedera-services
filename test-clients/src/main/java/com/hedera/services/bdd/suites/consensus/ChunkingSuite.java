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
package com.hedera.services.bdd.suites.consensus;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.chunkAFile;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChunkingSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ChunkingSuite.class);
    private static final int CHUNK_SIZE = 1024;

    public static void main(String... args) {
        new ChunkingSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                chunkNumberIsValidated(),
                chunkTransactionIDIsValidated(),
                longMessageIsFragmentedIntoChunks());
    }

    private HapiSpec chunkNumberIsValidated() {
        return defaultHapiSpec("chunkNumberIsValidated")
                .given(createTopic("testTopic"))
                .when()
                .then(
                        submitMessageTo("testTopic")
                                .message("failsForChunkNumberGreaterThanTotalChunks")
                                .chunkInfo(2, 3)
                                .hasRetryPrecheckFrom(BUSY)
                                .hasKnownStatus(INVALID_CHUNK_NUMBER),
                        submitMessageTo("testTopic")
                                .message("acceptsChunkNumberLessThanTotalChunks")
                                .chunkInfo(3, 2)
                                .hasRetryPrecheckFrom(BUSY)
                                .hasKnownStatus(SUCCESS),
                        submitMessageTo("testTopic")
                                .message("acceptsChunkNumberEqualTotalChunks")
                                .chunkInfo(5, 5)
                                .hasRetryPrecheckFrom(BUSY)
                                .hasKnownStatus(SUCCESS));
    }

    private HapiSpec chunkTransactionIDIsValidated() {
        return defaultHapiSpec("chunkTransactionIDIsValidated")
                .given(cryptoCreate("initialTransactionPayer"), createTopic("testTopic"))
                .when()
                .then(
                        submitMessageTo("testTopic")
                                .message("failsForDifferentPayers")
                                .chunkInfo(3, 2, "initialTransactionPayer")
                                .hasRetryPrecheckFrom(BUSY)
                                .hasKnownStatus(INVALID_CHUNK_TRANSACTION_ID),
                        /* AcceptsChunkNumberDifferentThan1HavingTheSamePayerEvenWhenNotMatchingValidStart */
                        submitMessageTo("testTopic")
                                .message("A")
                                .chunkInfo(3, 3, "initialTransactionPayer")
                                .payingWith("initialTransactionPayer")
                                // Add delay to make sure the valid start of the transaction will
                                // not match
                                // that of the initialTransactionID
                                .delayBy(1000)
                                .hasRetryPrecheckFrom(BUSY)
                                .hasKnownStatus(SUCCESS),
                        /* FailsForTransactionIDOfChunkNumber1NotMatchingTheEntireInitialTransactionID */
                        submitMessageTo("testTopic")
                                .message("B")
                                .chunkInfo(2, 1)
                                // Also add delay here
                                .delayBy(1000)
                                .hasRetryPrecheckFrom(BUSY)
                                .hasKnownStatus(INVALID_CHUNK_TRANSACTION_ID),
                        /* AcceptsChunkNumber1WhenItsTransactionIDMatchesTheEntireInitialTransactionID */
                        submitMessageTo("testTopic")
                                .message("C")
                                .chunkInfo(4, 1)
                                .via("firstChunk")
                                .payingWith("initialTransactionPayer")
                                .usePresetTimestamp()
                                .hasRetryPrecheckFrom(BUSY)
                                .hasKnownStatus(SUCCESS));
    }

    private HapiSpec longMessageIsFragmentedIntoChunks() {
        String fileForLongMessage = "src/main/resource/RandomLargeBinary.bin";
        return defaultHapiSpec("longMessageIsFragmentedIntoChunks")
                .given(cryptoCreate("payer"), createTopic("testTopic"))
                .when()
                .then(chunkAFile(fileForLongMessage, CHUNK_SIZE, "payer", "testTopic"));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
