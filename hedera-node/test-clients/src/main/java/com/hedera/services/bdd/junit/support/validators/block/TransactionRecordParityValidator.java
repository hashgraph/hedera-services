package com.hedera.services.bdd.junit.support.validators.block;

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromPbj;
import static com.hedera.node.app.hapi.utils.CommonPbjConverters.pbjToProto;
import static com.hedera.services.bdd.junit.support.BlockStreamAccess.BLOCK_STREAM_ACCESS;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.transaction.TransactionRecord;
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

    //todo rework after using block-0.0.3.tar.gz
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
        if (txnRecs.isEmpty()) {
            Assertions.fail("Expected a non-empty collection of record items");
        }


        logger.info("Parsed {} record items from {} records", txnRecs.size(), numRecords);
        return txnRecs;
    }

    @Override
    public void validateBlockVsRecords(@NonNull final List<Block> blocks, @NonNull final
            RecordStreamAccess.Data data) {
        final var inputs = parseBlocks(blocks);
        final var expectedTxnRecs = shapeRecords(data);

        logger.info("Validating transaction record parity...");

        //todo which state changes? (this is obviously wrong, but to get it to compile..)
        final var singleTxnRecs = TRANSACTION_RECORD_TRANSLATOR.translateAll(inputs.txns(), inputs.allStateChanges().get(0));
        final var expected = expectedTxnRecs.stream().map(e -> {
                    final var consensusTimestamp = e.getRecord().getConsensusTimestamp();
            return new RecordStreamEntry(TransactionParts.from(e.getTransaction()), e.getRecord(), fromTimestamp(consensusTimestamp));
                }).toList();
        final var actual = singleTxnRecs.stream().map(txnRecord -> {
            final var parts = TransactionParts.from(fromPbj(txnRecord.transaction()));
                    final var consensusTimestamp = txnRecord.transactionRecord().consensusTimestamp();
                    return new RecordStreamEntry(parts, pbjToProto(txnRecord.transactionRecord(), TransactionRecord.class, com.hederahashgraph.api.proto.java.TransactionRecord.class), fromTimestamp(fromTimestamp(consensusTimestamp)));
                })
                .sorted()
                .collect(Collectors.toList());

        final var rcDiff = new RcDiff(Long.MAX_VALUE, Long.MAX_VALUE, expected, actual, null, System.out);
        try {
            final var result = rcDiff.summarizeDiffs();
            if (result.isEmpty()) {
                //todo output success
                logger.info("Validation complete. Summary: \n{}", "<summary here>");
            } else {
                final var summary = rcDiff.buildDiffOutput(result);
                logger.error("Found errors, validation failed!\nDiffs:");
                summary.forEach(logger::error);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final TransactionRecordTranslator<SingleTransactionBlockItems> TRANSACTION_RECORD_TRANSLATOR = new BlockStreamTransactionTranslator();

    private static Instant fromTimestamp(final Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private static Timestamp fromTimestamp(final com.hedera.hapi.node.base.Timestamp timestamp) {
        return Timestamp.newBuilder().setSeconds(timestamp.seconds()).setNanos(timestamp.nanos()).build();
    }
}
