package com.hedera.services.bdd.junit.support.validators.block;

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromPbj;
import static com.hedera.node.app.hapi.utils.CommonPbjConverters.toPbj;
import static com.hedera.services.bdd.junit.support.BlockStreamAccess.BLOCK_STREAM_ACCESS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotModeOp.exactMatch;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hedera.node.app.hapi.utils.forensics.OrderedComparison;
import com.hedera.node.app.hapi.utils.forensics.RecordStreamEntry;
import com.hedera.node.app.hapi.utils.forensics.TransactionParts;
import com.hedera.services.bdd.junit.support.BlockStreamValidator;
import com.hedera.services.bdd.junit.support.RecordStreamAccess;
import com.hedera.services.bdd.junit.support.translators.BlockStreamTransactionTranslator;
import com.hedera.services.bdd.junit.support.translators.SingleTransactionBlockItems;
import com.hedera.services.bdd.junit.support.translators.TransactionRecordTranslator;
import com.hedera.services.bdd.utils.RcDiff;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hederahashgraph.api.proto.java.Timestamp;

import org.junit.jupiter.api.Assertions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class TransactionRecordParityValidator implements BlockStreamValidator {
    private static final Logger logger = LogManager.getLogger(StateChangesValidator.class);

    public static void main(String[] args) throws IOException {
//        var DONTUSEINCODE =
//                "/Users/matthess/Desktop/copy_vanillaTransferSucceeds_block-and-records_2aug2024/";
        var DONTUSEINCODE = "/Users/matthess/Downloads/";
        final var testBlocksLoc = DONTUSEINCODE + "block-0.0.3";
        final var blocks = BLOCK_STREAM_ACCESS.readBlocks(Paths.get(testBlocksLoc));
        final var recordsLoc = DONTUSEINCODE + "record0.0.3";
        final var records = RecordStreamAccess.RECORD_STREAM_ACCESS.readStreamDataFrom(Paths.get(recordsLoc).toString(), "sidecar");

        // Perform the validation(s)
        new TransactionRecordParityValidator().validateBlockVsRecords(blocks, records);
    }

    private record ActualInputs(List<SingleTransactionBlockItems> txns, List<StateChanges> allStateChanges) {}

    private ActualInputs parseBlocks(@NonNull final List<Block> blocks) {
        final var allStateChanges = new ArrayList<StateChanges>();
        final var blockTxns = new ArrayList<SingleTransactionBlockItems>();
        for (var block : blocks) {
            final var items = block.items();

            SingleTransactionBlockItems.Builder builder = new SingleTransactionBlockItems.Builder();
            for (var item : items) {
                if (item.hasTransaction()) {
                    builder.txn(item.transaction());
                } else if (item.hasTransactionResult()) {
                    builder.result(item.transactionResult());
                } else if (item.hasTransactionOutput()) {
                    builder.output(item.transactionOutput());
                } else if (item.hasStateChanges()) {
                    var stateChanges = item.stateChanges();
                    allStateChanges.add(stateChanges);

                    // with state changes, we have all the parts of a transaction. Therefore, build the txn and reassign the builder
                    final var SingleTransactionBlockItems = builder.build();
                    blockTxns.add(SingleTransactionBlockItems);
                    builder = new SingleTransactionBlockItems.Builder();
                }
            }
        }

        if (blockTxns.isEmpty()) { Assertions.fail("Needs at least one block transaction"); }

        logger.info("Parsed {} blocks (with {} transactions)", blocks.size(), blockTxns.size());
        return new ActualInputs(blockTxns, allStateChanges);
    }

    private List<RecordStreamItem> shapeRecords(@NonNull final RecordStreamAccess.Data data) {
        final var numRecords = data.records().size();
        final var txnRecs = data.records().stream()
                .flatMap(record -> record.recordFile().getRecordStreamItemsList().stream())
                .toList();
        if (txnRecs.isEmpty()) { Assertions.fail("Expected a non-empty collection of record items"); }


        logger.info("Parsed {} record items from {} records", txnRecs.size(), numRecords);
        return txnRecs;
    }


    public static class OrderingChecker {
        enum Ordering {
            CORRECT,
            CORRECT_BEFORE_AFTER_BUT_INDEX_WRONG,
            CORRECT_ID_BUT_BEFORE_AFTER_WRONG,
            INCORRECT_ID_AND_BEFORE_AFTER,    // 'before' or(/and) 'after' is wrong
            NOT_FOUND
        }

        record OrderingResult(com.hederahashgraph.api.proto.java.TransactionID expectedBefore, com.hederahashgraph.api.proto.java.TransactionID expected, com.hederahashgraph.api.proto.java.TransactionID expectedAfter, int actualIndexOfExpected, com.hederahashgraph.api.proto.java.TransactionID actualBefore, com.hederahashgraph.api.proto.java.TransactionID actualIdAtExpectedIndex, com.hederahashgraph.api.proto.java.TransactionID actualAfter, Ordering ordering, String message) {}

        private List<OrderingResult> checkOrdering(@NonNull final List<RecordStreamItem> txnRecs, @NonNull final List<SingleTransactionBlockItems> blockTxns) {
            final var expectedTxnIds = txnRecs.stream().map(t->t.getRecord().getTransactionID()).toList();
            final var actualTxnIds = blockTxns.stream().map(t -> fromPbj(t.txn().bodyOrThrow().transactionID())).toList();

            final var results = new ArrayList<OrderingResult>();
            for (int i = 0; i < expectedTxnIds.size(); i++) {
                final var expectedId = expectedTxnIds.get(i);
                final var actualId = actualTxnIds.get(i);
                final var expectedBefore = i == 0 ? null : expectedTxnIds.get(i - 1);
                final var expectedAfter = i == expectedTxnIds.size() - 1 ? null : expectedTxnIds.get(i + 1);
                final var actualIndex = actualTxnIds.indexOf(expectedId);
                if (actualIndex < 0) {
                    results.add(
                            new OrderingResult(expectedBefore, expectedId, expectedAfter, -1, actualId, null, null,
                                    Ordering.NOT_FOUND, ""));
                    continue;
                }

                final var actualBefore = actualIndex == 0 ? null : actualTxnIds.get(actualIndex - 1);
                final var actualAfter = actualIndex == actualTxnIds.size() - 1 ? null : actualTxnIds.get(actualIndex + 1);
                final Ordering ordering;
                if (actualIndex == i) {
                    // Case: the actual ID, its previous ID, and its next ID are all correct (and the index is correct)
                    if (Objects.equals(expectedId, actualId) && Objects.equals(expectedBefore, actualBefore) && Objects.equals(expectedAfter, actualAfter)) {
                        ordering = Ordering.CORRECT;
                    }
                    // Case: the actual ID and its index are correct, but the 'before' or 'after' ID is wrong
                    else /* if (Objects.equals(expectedId, actualId)) */ {
                        ordering = Ordering.CORRECT_ID_BUT_BEFORE_AFTER_WRONG;
                    }
                } else {
                    // Case: the actual ID, its previous ID, and its next ID are all correct, but the index is wrong
                    if (Objects.equals(expectedBefore, actualBefore) && Objects.equals(expectedAfter, actualAfter) && Objects.equals(expectedId, actualId)) {
                        ordering = Ordering.CORRECT_BEFORE_AFTER_BUT_INDEX_WRONG;
                    }
                    // Case: the actual ID is correct, but the 'before' or 'after' is wrong and the index is wrong
                    else if (Objects.equals(expectedId, actualId)) {
                        ordering = Ordering.CORRECT_ID_BUT_BEFORE_AFTER_WRONG;
                    }
                    // Case: neither the actual ID nor its 'before' / 'after' objects are correct
                    else {
                        ordering = Ordering.INCORRECT_ID_AND_BEFORE_AFTER;
                    }
                }
                results.add(new OrderingResult(expectedBefore, expectedId, expectedAfter,
                        actualIndex, actualId, actualBefore, actualAfter, ordering, ""));
            }

            return results;
        }
    }

    @Override
    public void validateBlockVsRecords(@NonNull final List<Block> blocks, @NonNull final
            RecordStreamAccess.Data data) {
        final var inputs = parseBlocks(blocks);
//        final var b = inputs.txns().stream().collect(Collectors.toMap(t -> t.txn().bodyOrThrow().transactionID(), Function.identity()));
        final var expectedTxnRecs = shapeRecords(data);
        final var txnRecsByTxnId = expectedTxnRecs.stream().map(RecordStreamItem::getRecord).map(CommonPbjConverters::toPbj)
                .collect(Collectors.toMap(TransactionRecord::transactionIDOrThrow,
                        Function.identity()));

        logger.info("Validating transaction record parity...");

//        final var results = compareIndividualTxns(b, txnRecsByTxnId, inputs.allStateChanges());
        //todo state changes set

        final var singleTxnRecs = TRANSACTION_RECORD_TRANSLATOR.translateAll(inputs.txns(), inputs.allStateChanges().get(0));

        var expected = expectedTxnRecs.stream().map(e -> {
                    final var consensusTimestamp = e.getRecord().getConsensusTimestamp();
            return new RecordStreamEntry(TransactionParts.from(e.getTransaction()), e.getRecord(), fromTimestamp(consensusTimestamp));
                }).toList();
        var actualTxnRecsAsRecEntries = singleTxnRecs.stream().map(txnRecord -> {
            final var parts = TransactionParts.from(fromPbj(txnRecord.transaction()));
                    final var consensusTimestamp = txnRecord.transactionRecord().consensusTimestamp();
                    return new RecordStreamEntry(parts, fromPbj(txnRecord.transactionRecord()), fromTimestamp(fromTimestamp(consensusTimestamp)));
                })
                .sorted()
                .collect(Collectors.toList());


        PrintStream out = System.out;
        final var rcDiff = new RcDiff(Long.MAX_VALUE, Long.MAX_VALUE, expected, actualTxnRecsAsRecEntries, null,
                out);
        try {
            final var result = rcDiff.summarizeDiffs();
            if (result.isEmpty()) {
                // success
            } else {
                final var summary = rcDiff.buildDiffOutput(result);
                logger.error("Found errors, validation failed!\nDiffs:");
                summary.forEach(logger::error);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        // todo: we should validate the ORDER of the txns too
        final var results2 = new OrderingChecker().checkOrdering(expectedTxnRecs, inputs.txns());

        // todo: validate running hash?

        //todo summary
        logger.info("Validation complete. Summary: \n{}", buildSummary(results));
    }

    private String buildSummary(@NonNull final List<ValidationResult> results) {
        final var sb = new StringBuilder().append("Validation results:\n---------\n");
        for (final var result : results) {
            sb.append("Txn ").append(result.txnId).append(" ");
            sb.append(result.display());
            sb.append("\n---\n");
        }
        return sb.toString();
    }

    private List<ValidationResult> compareIndividualTxns(@NonNull final Map<TransactionID, SingleTransactionBlockItems> txnBlockItemsById, @NonNull final
            Map<TransactionID, TransactionRecord> txnRecsByTxnId, @NonNull final List<StateChanges> allStateChanges) {
        final var results = new ArrayList<ValidationResult>();
        for (final var txnRec : txnRecsByTxnId.entrySet()) {
            final var txnId = txnRec.getKey();
            final var expected = txnRec.getValue();
            final var actual = txnBlockItemsById.get(txnId);

            if (expected == null) {
                results.add(new ValidationResult(txnId, null, actual, null,  "No transaction record found"));
                continue;
            } else if (actual == null) {
                results.add(new ValidationResult(txnId, expected, actual, null, "No block transaction found"));
                continue;
            }

            //todo this is obviously wrong (allStateChanges.get(0))
            final var translatedActual = TRANSACTION_RECORD_TRANSLATOR.translate(actual, allStateChanges.get(0));
            final var mismatch = RECORD_DIFF_SUMMARIZER.apply(fromPbj(expected),
                    fromPbj(translatedActual.transactionRecord()));

            results.add(new ValidationResult(txnId, expected, actual, null, mismatch));

//            if (!expected.equals(translatedActual)) {
//            }
        }

        return results;
    }

    private static final OrderedComparison.RecordDiffSummarizer RECORD_DIFF_SUMMARIZER = (a, b) -> {
        try {
            exactMatch(a, b, () -> "");
        } catch (Throwable t) {
            return t.getMessage();
        }
        throw new AssertionError("No difference to summarize");
    };

    private static final TransactionRecordTranslator<SingleTransactionBlockItems> TRANSACTION_RECORD_TRANSLATOR = new BlockStreamTransactionTranslator();

    private record ValidationResult(@NonNull TransactionID txnId, @Nullable TransactionRecord expected, @Nullable SingleTransactionBlockItems actual,
                                    @Nullable Exception cause, @Nullable String display) {}

    private static Instant fromTimestamp(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private static Timestamp fromTimestamp(com.hedera.hapi.node.base.Timestamp timestamp) {
        return Timestamp.newBuilder().setSeconds(timestamp.seconds()).setNanos(timestamp.nanos()).build();
    }
}
