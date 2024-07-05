/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.genesis;

import static com.hedera.node.app.hapi.utils.forensics.OrderedComparison.statusHistograms;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.HapiSpecSetup.DEFAULT_CONFIG;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.streamMustIncludeNoFailuresFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateVisibleItems;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.visibleItems;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.node.app.hapi.utils.forensics.RecordStreamEntry;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.services.bdd.junit.GenesisHapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.streams.assertions.VisibleItemsAssertion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

/**
 * Tests
 */
public class GenesisSetupTest {
    @GenesisHapiTest
    final Stream<DynamicTest> syntheticFileCreationsMatchQueries() {
        final AtomicReference<VisibleItemsAssertion> assertion = new AtomicReference<>();
        return hapiTest(
                streamMustIncludeNoFailuresFrom(visibleItems(assertion, "genesisTxn")),
                cryptoCreate("firstUser").via("genesisTxn"),
                validateVisibleItems(assertion, GenesisSetupTest::validateSystemFileExports),
                // Assert the first created entity is 0.0.1001
                withOpContext((spec, opLog) -> assertEquals(
                        DEFAULT_CONFIG.getConfigData(HederaConfig.class).firstUserEntity(),
                        spec.registry().getAccountID("firstUser").getAccountNum(),
                        "First user entity num doesn't match config")));
    }

    private static void validateSystemFileExports(
            @NonNull final HapiSpec spec, @NonNull final Map<String, List<RecordStreamEntry>> genesisRecords) {
        final var filesConfig = DEFAULT_CONFIG.getConfigData(FilesConfig.class);
        final var items = requireNonNull(genesisRecords.get("genesisTxn"));
        final var histogram = statusHistograms(items);
        assertEquals(Map.of(SUCCESS, 17), histogram.get(FileCreate));
        validateSystemFile(filesConfig.addressBook(), spec, items);
        validateSystemFile(filesConfig.nodeDetails(), spec, items);
        validateSystemFile(filesConfig.feeSchedules(), spec, items);
        validateSystemFile(filesConfig.exchangeRates(), spec, items);
        validateSystemFile(filesConfig.networkProperties(), spec, items);
        validateSystemFile(filesConfig.hapiPermissions(), spec, items);
        validateSystemFile(filesConfig.throttleDefinitions(), spec, items);
        final var updateFilesRange = filesConfig.softwareUpdateRange();
        for (long i = updateFilesRange.left(); i <= updateFilesRange.right(); i++) {
            validateSystemFile(i, spec, items);
        }
    }

    private static void validateSystemFile(
            final long fileNum, @NonNull final HapiSpec spec, @NonNull final List<RecordStreamEntry> items) {
        final var syntheticCreation = items.stream()
                .filter(item ->
                        item.function() == FileCreate && item.createdFileId().getFileNum() == fileNum)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No synthetic creation for 0.0." + fileNum));
        final var expectedContents = syntheticCreation.body().getFileCreate().getContents();
        final var query = getFileContents("0.0." + fileNum);
        allRunFor(spec, query);
        final var actualContents =
                query.getResponse().getFileGetContents().getFileContents().getContents();
        assertEquals(expectedContents, actualContents, "0.0." + fileNum + " contents don't match state");
    }
}
