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

package com.hedera.services.bdd.junit.support.validators.block;

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromPbj;
import static com.hedera.node.app.hapi.utils.CommonPbjConverters.pbjToProto;
import static com.hedera.services.bdd.spec.TargetNetworkType.SUBPROCESS_NETWORK;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.hapi.utils.forensics.DifferingEntries;
import com.hedera.node.app.hapi.utils.forensics.RecordStreamEntry;
import com.hedera.node.app.hapi.utils.forensics.TransactionParts;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.support.BlockStreamValidator;
import com.hedera.services.bdd.junit.support.RecordStreamAccess;
import com.hedera.services.bdd.junit.support.translators.BlockStreamTransactionTranslator;
import com.hedera.services.bdd.junit.support.translators.SingleTransactionBlockItems;
import com.hedera.services.bdd.junit.support.translators.TransactionRecordTranslator;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.utils.RcDiff;
import com.hederahashgraph.api.proto.java.Timestamp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class TransactionRecordParityValidator implements BlockStreamValidator {
    private static final Logger logger = LogManager.getLogger(TransactionRecordParityValidator.class);

    public static final Factory FACTORY = new Factory() {
        @NonNull
        @Override
        public TransactionRecordParityValidator create(@NonNull final HapiSpec spec) {
            return newValidatorFor(spec);
        }

        @Override
        public boolean appliesTo(@NonNull HapiSpec spec) {
            // Embedded networks don't have saved states or a Merkle tree to validate hashes against
            return spec.targetNetworkOrThrow().type() == SUBPROCESS_NETWORK;
        }
    };

    private static TransactionRecordParityValidator newValidatorFor(HapiSpec spec) {
        return new TransactionRecordParityValidator();
    }

    @Override
    public void validateBlockVsRecords(@NonNull final List<Block> blocks, @NonNull final RecordStreamAccess.Data data) {
        // Parse the input blocks
        final BlocksData inputs;
        try {
            inputs = new BlocksParser().parseBlocks(blocks);
        } catch (ParseException e) {
            Assertions.fail("Failed to parse blocks.", e);
            return;
        }

        // Transform the expected transaction records into the required format
        final var expectedTxnRecs = transformExpectedRecords(data);

        final var actual = translateAll(inputs);

        final var maxDiffs = 1000;
        final var lenOfDiffSecs = 300;
        final var rcDiff = new RcDiff(maxDiffs, lenOfDiffSecs, expectedTxnRecs, actual, null, System.out);
        // Perform the diff
        final List<DifferingEntries> diffs;
        try {
            diffs = rcDiff.summarizeDiffs();
        } catch (Exception e) {
            Assertions.fail("Failed to compare all records.", e);
            return;
        }

        // TODO: pass in `inputs.allStateChanges().size()` instead of zero (when the method called
        // actually does something with the state changes)
        final var validatorSummary = new SummaryBuilder(
                        maxDiffs,
                        lenOfDiffSecs,
                        blocks.size(),
                        data.records().size(),
                        inputs.txns().size(),
                        0, // Will be inputs.allStateChanges().size()
                        diffs)
                .build();
        if (diffs.isEmpty()) {
            logger.info("Validation complete. Summary: {}", validatorSummary);
        } else {
            final var rcDiffSummary = rcDiff.buildDiffOutput(diffs);
            logger.error("Found errors, validation failed!");
            rcDiffSummary.forEach(logger::error);
            logger.error("Validation failed. Summary: {}", validatorSummary);
        }
    }

    private List<RecordStreamEntry> translateAll(final BlocksData blocksData) {
        // Translate each block transaction into a SingleTransactionRecord instance
        final var singleTxnRecs =
                TRANSACTION_RECORD_TRANSLATOR.translateAll(blocksData.txns(), blocksData.allStateChanges());
        // Shape the translated records into RecordStreamEntry instances
        return singleTxnRecs.stream()
                .map(txnRecord -> {
                    final var parts = TransactionParts.from(fromPbj(txnRecord.transaction()));
                    final var consensusTimestamp = txnRecord.transactionRecord().consensusTimestamp();
                    return new RecordStreamEntry(
                            parts,
                            pbjToProto(
                                    txnRecord.transactionRecord(),
                                    TransactionRecord.class,
                                    com.hederahashgraph.api.proto.java.TransactionRecord.class),
                            fromTimestamp(fromTimestamp(consensusTimestamp)));
                })
                .sorted()
                .collect(Collectors.toList());
    }

    private List<RecordStreamEntry> transformExpectedRecords(@NonNull final RecordStreamAccess.Data data) {
        final var numRecords = data.records().size();
        final var txnRecs = data.records().stream()
                .flatMap(record -> record.recordFile().getRecordStreamItemsList().stream())
                .map(expectedTxn -> {
                    final var consensusTimestamp = expectedTxn.getRecord().getConsensusTimestamp();
                    return new RecordStreamEntry(
                            TransactionParts.from(expectedTxn.getTransaction()),
                            expectedTxn.getRecord(),
                            fromTimestamp(consensusTimestamp));
                })
                .toList();
        if (txnRecs.isEmpty()) {
            Assertions.fail("Expected a non-empty collection of record items");
        }

        logger.info("Parsed {} record items from {} records", txnRecs.size(), numRecords);
        return txnRecs;
    }

    private static Instant fromTimestamp(final Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private static Timestamp fromTimestamp(final com.hedera.hapi.node.base.Timestamp timestamp) {
        return Timestamp.newBuilder()
                .setSeconds(timestamp.seconds())
                .setNanos(timestamp.nanos())
                .build();
    }

    private record BlocksData(List<SingleTransactionBlockItems> txns, List<StateChanges> allStateChanges) {}

    private static class BlocksParser {
        private SingleTransactionBlockItems.Builder builder = new SingleTransactionBlockItems.Builder();
        private final List<SingleTransactionBlockItems> blockTxns = new ArrayList<>();
        private final List<StateChanges> allStateChanges = new ArrayList<>();

        private BlocksParser() {}

        BlocksData parseBlocks(@NonNull final List<Block> blocks) throws ParseException {
            for (final var block : blocks) {
                final var items = block.items();

                // A new block is starting, so any (non-empty) transaction that was in progress needs to be built
                if (!builder.isEmpty()) {
                    final var SingleTransactionBlockItems = builder.build();
                    blockTxns.add(SingleTransactionBlockItems);
                }
                builder = new SingleTransactionBlockItems.Builder();
                for (final var item : items) {
                    if (item.hasEventHeader()) {
                        // Since transactions can only be inside of events, and a new event header is the next item,
                        // the last transaction can't have anything else in it, and needs to be built
                        if (!builder.isEmpty()) {
                            final var SingleTransactionBlockItems = builder.build();
                            blockTxns.add(SingleTransactionBlockItems);
                            builder = new SingleTransactionBlockItems.Builder();
                        }
                    }
                    if (item.hasEventTransaction()) {
                        // A new transaction has started, so we need to build the previous one (if it isn't empty)
                        if (!builder.isEmpty()) {
                            final var SingleTransactionBlockItems = builder.build();
                            blockTxns.add(SingleTransactionBlockItems);
                            builder = new SingleTransactionBlockItems.Builder();
                        }

                        final var submittedTxnBytes = item.eventTransaction().applicationTransactionOrElse(Bytes.EMPTY);
                        if (!(Objects.equals(submittedTxnBytes, Bytes.EMPTY))) {
                            final var submittedTxn = Transaction.PROTOBUF.parse(submittedTxnBytes);
                            builder.txn(submittedTxn);
                        }
                    } else if (item.hasTransactionResult()) {
                        builder.result(item.transactionResult());
                    } else if (item.hasTransactionOutput()) {
                        builder.output(item.transactionOutput());
                    } else if (item.hasStateChanges()) {
                        final var stateChanges = item.stateChanges();
                        allStateChanges.add(stateChanges);

                        // Now that we have the state changes, there's nothing else that can be part
                        // of a single transaction, so we build the transaction and reset the builder
                        if (!builder.isEmpty()) {
                            final var SingleTransactionBlockItems = builder.build();
                            blockTxns.add(SingleTransactionBlockItems);
                            builder = new SingleTransactionBlockItems.Builder();
                        }
                    }
                }
            }

            if (blockTxns.isEmpty()) {
                Assertions.fail("Needs at least one block transaction");
            }

            logger.info("Parsed {} blocks (with {} transactions)", blocks.size(), blockTxns.size());
            return new BlocksData(blockTxns, allStateChanges);
        }
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

    private static final TransactionRecordTranslator<SingleTransactionBlockItems> TRANSACTION_RECORD_TRANSLATOR =
            new BlockStreamTransactionTranslator();
}
