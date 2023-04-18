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

package com.hedera.node.app.service.mono.stream;

import static com.hedera.node.app.service.mono.stream.RecordStreamRecoveryTest.ALL_EXPECTED_RSOS_ASSET;
import static com.hedera.node.app.service.mono.stream.RecordStreamRecoveryTest.ON_DISK_FILES_LOC;
import static com.hedera.node.app.service.mono.stream.RecordStreamRecoveryTest.RECOVERY_STREAM_ONLY_RSOS_ASSET;
import static com.hedera.node.app.service.mono.stream.RecordStreamRecoveryTest.loadRecoveryRsosFrom;
import static com.hedera.node.app.service.mono.stream.RecordStreamRecoveryTest.rsoFrom;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecoveryRecordsWriterTest {
    @Mock
    private RecordStreamFileWriter recordStreamFileWriter;

    private RecoveryRecordsWriter subject;

    @BeforeEach
    void setUp() {
        subject = new RecoveryRecordsWriter(2_000L, ON_DISK_FILES_LOC);
    }

    @Test
    void replaysMissingPrefix() throws InvalidProtocolBufferException {
        final var allExpectedRsos = loadRecoveryRsosFrom(ALL_EXPECTED_RSOS_ASSET);
        final var recoveryRsos = loadRecoveryRsosFrom(RECOVERY_STREAM_ONLY_RSOS_ASSET);

        final var numOmittedInRecoveryStream = allExpectedRsos.size() - recoveryRsos.size();
        final var firstRso = rsoFrom(recoveryRsos.get(numOmittedInRecoveryStream));
        System.out.println("First RSO: " + firstRso.getTimestamp());
        subject.writeRecordPrefixForRecoveryStartingWith(firstRso, recordStreamFileWriter);

        final var onDiskPrefix = applicableOnDiskPrefixGiven(allExpectedRsos, numOmittedInRecoveryStream);
        // Verify the first RSO is marked as starting a new file
        onDiskPrefix.get(0).setWriteNewFile();
        for (final var rso : onDiskPrefix) {
            Mockito.verify(recordStreamFileWriter).addObject(rso);
        }
    }

    @Test
    void propagatesMissingOnDiskDirectory() {
        subject = new RecoveryRecordsWriter(2_000L, "missingOnDiskRecords");

        final var mockFirstRso = new RecordStreamObject(
                TransactionRecord.getDefaultInstance(), Transaction.getDefaultInstance(), Instant.now());
        assertThrows(
                UncheckedIOException.class,
                () -> subject.writeRecordPrefixForRecoveryStartingWith(mockFirstRso, recordStreamFileWriter));
    }

    @Test
    void filtersFilesWhoseConsensusIntervalDoesntIncludeFirstRecoverTime() {
        final var tooEarlyFileTime = "2023-04-18T14_08_19.000000001Z.rcd.gz";
        final var applicableFileTime = "2023-04-18T14_08_20.465612003Z.rcd.gz";
        final var tooLateFileTime = "2023-04-18T14_08_22.967161003Z.rcd.gz";
        final var firstRecoveryTime = Instant.parse("2023-04-18T14:08:21.968217003Z");

        final var filterSubject = RecoveryRecordsWriter.inclusionTestFor(firstRecoveryTime, 2_000L);

        Assertions.assertFalse(filterSubject.test(tooEarlyFileTime));
        Assertions.assertFalse(filterSubject.test(tooLateFileTime));
        Assertions.assertTrue(filterSubject.test(applicableFileTime));
    }

    private List<RecordStreamObject> applicableOnDiskPrefixGiven(
            final List<RecoveryRSO> allExpectedRsos, final int numOmittedInRecoveryStream) {
        return allExpectedRsos.subList(0, numOmittedInRecoveryStream).stream()
                .map(recoveryRso -> {
                    try {
                        return rsoFrom(recoveryRso);
                    } catch (InvalidProtocolBufferException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();
    }
}
