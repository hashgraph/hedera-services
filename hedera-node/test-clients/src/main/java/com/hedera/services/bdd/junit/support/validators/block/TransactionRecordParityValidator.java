/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.support.validators.block;

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromPbj;
import static com.hedera.node.app.hapi.utils.CommonPbjConverters.pbjToProto;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.workingDirFor;
import static com.hedera.services.bdd.spec.TargetNetworkType.SUBPROCESS_NETWORK;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.hapi.utils.forensics.DifferingEntries;
import com.hedera.node.app.hapi.utils.forensics.RecordStreamEntry;
import com.hedera.node.app.hapi.utils.forensics.TransactionParts;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.services.bdd.junit.support.BlockStreamAccess;
import com.hedera.services.bdd.junit.support.BlockStreamValidator;
import com.hedera.services.bdd.junit.support.StreamFileAccess;
import com.hedera.services.bdd.junit.support.translators.BlockTransactionalUnitTranslator;
import com.hedera.services.bdd.junit.support.translators.BlockUnitSplit;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.utils.RcDiff;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

/**
 * A validator that asserts the block stream contains all information previously exported in the record stream
 * by translating the block stream into transaction records and comparing them to the expected records.
 */
public class TransactionRecordParityValidator implements BlockStreamValidator {
    private static final int MAX_DIFFS_TO_REPORT = 10;
    private static final int DIFF_INTERVAL_SECONDS = 300;
    private static final Logger logger = LogManager.getLogger(TransactionRecordParityValidator.class);

    private final BlockUnitSplit blockUnitSplit = new BlockUnitSplit();
    private final BlockTransactionalUnitTranslator translator;

    public static final Factory FACTORY = new Factory() {
        @Override
        public boolean appliesTo(@NonNull final HapiSpec spec) {
            requireNonNull(spec);
            // Embedded networks don't have saved states or a Merkle tree to validate hashes against
            return spec.targetNetworkOrThrow().type() == SUBPROCESS_NETWORK;
        }

        @Override
        public @NonNull TransactionRecordParityValidator create(@NonNull final HapiSpec spec) {
            return new TransactionRecordParityValidator();
        }
    };

    public TransactionRecordParityValidator() {
        translator = new BlockTransactionalUnitTranslator();
    }

    /**
     * A main method to run a standalone validation of the block stream against the record stream in this project.
     * @param args unused
     * @throws IOException if there is an error reading the block or record streams
     */
    public static void main(@NonNull final String[] args) throws IOException {
        final var node0Data = Paths.get("hedera-node/test-clients")
                .resolve(workingDirFor(0, "hapi").resolve("data"))
                .toAbsolutePath()
                .normalize();
        final var blocksLoc =
                node0Data.resolve("blockStreams/block-0.0.3").toAbsolutePath().normalize();
        final var blocks = BlockStreamAccess.BLOCK_STREAM_ACCESS.readBlocks(blocksLoc);
        final var recordsLoc =
                node0Data.resolve("recordStreams/record0.0.3").toAbsolutePath().normalize();
        final var records = StreamFileAccess.STREAM_FILE_ACCESS.readStreamDataFrom(recordsLoc.toString(), "sidecar");

        final var validator = new TransactionRecordParityValidator();
        validator.validateBlockVsRecords(blocks, records);
    }

    @Override
    public void validateBlockVsRecords(
            @NonNull final List<Block> blocks, @NonNull final StreamFileAccess.RecordStreamData data) {
        requireNonNull(blocks);
        requireNonNull(data);

        var foundGenesisBlock = false;
        for (final var block : blocks) {
            if (translator.scanBlockForGenesis(block)) {
                foundGenesisBlock = true;
                break;
            }
        }
        if (!foundGenesisBlock) {
            logger.error("Genesis block not found in block stream, at least some receipts will not match");
        }
        final var expectedEntries = data.records().stream()
                .flatMap(recordWithSidecars -> recordWithSidecars.recordFile().getRecordStreamItemsList().stream())
                .map(RecordStreamEntry::from)
                .toList();
        final var numStateChanges = new AtomicInteger();
        final List<RecordStreamEntry> actualEntries = blocks.stream()
                .flatMap(block -> blockUnitSplit.split(block).stream())
                .peek(unit -> numStateChanges.getAndAdd(unit.stateChanges().size()))
                .flatMap(unit -> translator.translate(unit).stream())
                .map(this::asEntry)
                .toList();
        final var rcDiff = new RcDiff(
                MAX_DIFFS_TO_REPORT, DIFF_INTERVAL_SECONDS, expectedEntries, actualEntries, null, System.out);
        final var diffs = rcDiff.summarizeDiffs();
        final var validatorSummary = new SummaryBuilder(
                        MAX_DIFFS_TO_REPORT,
                        DIFF_INTERVAL_SECONDS,
                        blocks.size(),
                        expectedEntries.size(),
                        actualEntries.size(),
                        numStateChanges.get(),
                        diffs)
                .build();
        if (diffs.isEmpty()) {
            logger.info("Validation complete. Summary: {}", validatorSummary);
        } else {
            final var diffOutput = rcDiff.buildDiffOutput(diffs);
            final var errorMsg = new StringBuilder()
                    .append(diffOutput.size())
                    .append(" differences found between translated and expected records");
            diffOutput.forEach(summary -> errorMsg.append("\n\n").append(summary));
            Assertions.fail(errorMsg.toString());
        }
    }

    private RecordStreamEntry asEntry(@NonNull final SingleTransactionRecord record) {
        final var parts = TransactionParts.from(fromPbj(record.transaction()));
        final var consensusTimestamp = record.transactionRecord().consensusTimestampOrThrow();
        return new RecordStreamEntry(
                parts,
                pbjToProto(
                        record.transactionRecord(),
                        TransactionRecord.class,
                        com.hederahashgraph.api.proto.java.TransactionRecord.class),
                Instant.ofEpochSecond(consensusTimestamp.seconds(), consensusTimestamp.nanos()));
    }

    private record SummaryBuilder(
            int maxDiffs,
            int lenOfDiffSecs,
            int numParsedBlockItems,
            int numExpectedRecords,
            int numInputTxns,
            int numStateChanges,
            List<DifferingEntries> result) {
        String build() {
            final var summary = new StringBuilder("\n")
                    .append("Max diffs used: ")
                    .append(maxDiffs)
                    .append("\n")
                    .append("Length of diff seconds used: ")
                    .append(lenOfDiffSecs)
                    .append("\n")
                    .append("Number of block items processed: ")
                    .append(numParsedBlockItems)
                    .append("\n")
                    .append("Number of record items processed: ")
                    .append(numExpectedRecords)
                    .append("\n")
                    .append("Number of (non-null) transaction items processed: ")
                    .append(numInputTxns)
                    .append("\n")
                    .append("Number of state changes processed: ")
                    .append(numStateChanges)
                    .append("\n")
                    .append("Number of errors: ")
                    .append(result.size()); // Report the count of errors (if any)

            return summary.toString();
        }
    }
}
