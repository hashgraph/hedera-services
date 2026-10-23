// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.junit.support.validators;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.bdd.junit.support.RecordWithSidecars;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ValidStartValidatorTest {
    private final ValidStartValidator validator = new ValidStartValidator();

    @ParameterizedTest
    @CsvSource({"99, 999999999, 100, 0", "100, 0, 100, 1", "100, 1, 100, 1"})
    void acceptsValidStartAtOrBeforeConsensusTime(
            final long startSeconds, final int startNanos, final long consensusSeconds, final int consensusNanos) {
        final var record = recordWithTimes(startSeconds, startNanos, consensusSeconds, consensusNanos)
                .build();

        assertDoesNotThrow(() -> validator.validateRecordsAndSidecars(List.of(fileWith(record))));
    }

    @ParameterizedTest
    @CsvSource({"101, 0, 100, 999999999", "100, 2, 100, 1"})
    void rejectsValidStartAfterConsensusTime(
            final long startSeconds, final int startNanos, final long consensusSeconds, final int consensusNanos) {
        final var record = recordWithTimes(startSeconds, startNanos, consensusSeconds, consensusNanos)
                .build();

        assertInvalid(record, "is after assigned consensus time");
    }

    @ParameterizedTest
    @CsvSource({"1, false", "0, true", "1, true"})
    void alsoValidatesChildAndScheduledRecords(final int nonce, final boolean scheduled) {
        final var record = recordWithTimes(101, 0, 100, 0);
        record.getTransactionIDBuilder().setNonce(nonce).setScheduled(scheduled);

        assertInvalid(record.build(), "is after assigned consensus time");
    }

    @Test
    void rejectsMissingTransactionId() {
        final var record = recordWithTimes(100, 0, 100, 0).clearTransactionID().build();

        assertInvalid(record, "Record has no transaction ID");
    }

    @Test
    void rejectsMissingValidStart() {
        final var record = recordWithTimes(100, 0, 100, 0);
        record.getTransactionIDBuilder().clearTransactionValidStart();

        assertInvalid(record.build(), "TransactionID has no valid start");
    }

    @Test
    void rejectsMissingConsensusTimestamp() {
        final var record =
                recordWithTimes(100, 0, 100, 0).clearConsensusTimestamp().build();

        assertInvalid(record, "Record has no consensus timestamp");
    }

    @Test
    void validatesEveryRecordAcrossFiles() {
        final var valid = recordWithTimes(100, 0, 100, 0).build();
        final var invalid = recordWithTimes(100, 1, 100, 0).build();
        final var files = List.of(fileWith(valid), fileWith(valid, invalid));

        assertThrows(AssertionError.class, () -> validator.validateRecordsAndSidecars(files));
    }

    @Test
    void acceptsEmptyStreams() {
        assertDoesNotThrow(() -> validator.validateRecordsAndSidecars(List.of()));
        assertDoesNotThrow(() -> validator.validateRecordsAndSidecars(List.of(fileWith())));
    }

    private void assertInvalid(final TransactionRecord record, final String message) {
        final var error = assertThrows(
                AssertionError.class, () -> validator.validateRecordsAndSidecars(List.of(fileWith(record))));
        assertTrue(error.getMessage().contains(message), error::getMessage);
    }

    private static TransactionRecord.Builder recordWithTimes(
            final long startSeconds, final int startNanos, final long consensusSeconds, final int consensusNanos) {
        return TransactionRecord.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(
                                Timestamp.newBuilder().setSeconds(startSeconds).setNanos(startNanos)))
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(consensusSeconds).setNanos(consensusNanos));
    }

    private static RecordWithSidecars fileWith(final TransactionRecord... records) {
        final var file = RecordStreamFile.newBuilder();
        for (final var record : records) {
            file.addRecordStreamItems(RecordStreamItem.newBuilder().setRecord(record));
        }
        return new RecordWithSidecars(file.build(), List.of());
    }
}
