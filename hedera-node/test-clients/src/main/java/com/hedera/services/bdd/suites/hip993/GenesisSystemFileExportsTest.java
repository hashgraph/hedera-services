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

package com.hedera.services.bdd.suites.hip993;

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromByteString;
import static com.hedera.node.app.hapi.utils.forensics.OrderedComparison.statusHistograms;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.streamMustIncludeNoFailuresFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateVisibleItems;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.visibleItems;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.grouping.GroupingVerbs.getSystemFiles;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NodeStakeUpdate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.FileID;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.GenesisHapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.grouping.SysFileLookups;
import com.hedera.services.bdd.spec.utilops.streams.assertions.VisibleItems;
import com.hedera.services.bdd.spec.utilops.streams.assertions.VisibleItemsAssertion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

/**
 * Asserts the synthetic file creations stipulated by HIP-993 match the file contents returned by the gRPC
 * API both before after the network has handled the genesis transaction. (It would be a annoyance for various tools
 * and tests if they needed to ensure a transaction was handled before issuing any {@code FileGetContents} queries
 * or submitting {@code FileUpdate} transactions.)
 */
public class GenesisSystemFileExportsTest {
    @GenesisHapiTest
    final Stream<DynamicTest> syntheticFileCreationsMatchQueries() {
        final AtomicReference<Map<FileID, Bytes>> preGenesisContents = new AtomicReference<>();
        final AtomicReference<VisibleItemsAssertion> assertion = new AtomicReference<>();
        return hapiTest(
                streamMustIncludeNoFailuresFrom(visibleItems(assertion, "genesisTxn")),
                getSystemFiles(preGenesisContents::set),
                cryptoCreate("firstUser").via("genesisTxn"),
                validateVisibleItems(assertion, validatorFor(preGenesisContents)),
                // Assert the first created entity still has the expected number
                withOpContext((spec, opLog) -> assertEquals(
                        spec.startupProperties().getLong("hedera.firstUserEntity"),
                        spec.registry().getAccountID("firstUser").getAccountNum(),
                        "First user entity num doesn't match config")));
    }

    private static BiConsumer<HapiSpec, Map<String, VisibleItems>> validatorFor(
            @NonNull final AtomicReference<Map<FileID, Bytes>> preGenesisContents) {
        return (spec, records) -> validateSystemFileExports(spec, records, preGenesisContents.get());
    }

    private static void validateSystemFileExports(
            @NonNull final HapiSpec spec,
            @NonNull final Map<String, VisibleItems> genesisRecords,
            @NonNull final Map<FileID, Bytes> preGenesisContents) {
        final var items = requireNonNull(genesisRecords.get("genesisTxn"));
        final var histogram = statusHistograms(items.entries());
        final var systemFileNums =
                SysFileLookups.allSystemFileNums(spec).boxed().toList();
        assertEquals(Map.of(SUCCESS, systemFileNums.size()), histogram.get(FileCreate));
        // Also check we export a node stake update at genesis
        assertEquals(Map.of(SUCCESS, 1), histogram.get(NodeStakeUpdate));
        final var postGenesisContents = SysFileLookups.getSystemFileContents(spec, fileNum -> true);
        items.entries().stream().filter(item -> item.function() == FileCreate).forEach(item -> {
            final var preContents = requireNonNull(
                    preGenesisContents.get(item.createdFileId()),
                    "No pre-genesis contents for " + item.createdFileId());
            final var postContents = requireNonNull(
                    postGenesisContents.get(item.createdFileId()),
                    "No post-genesis contents for " + item.createdFileId());
            final var exportedContents =
                    fromByteString(item.body().getFileCreate().getContents());
            assertEquals(
                    exportedContents, preContents, item.createdFileId() + " contents don't match pre-genesis query");
            assertEquals(
                    exportedContents, postContents, item.createdFileId() + " contents don't match post-genesis query");
        });
    }
}
