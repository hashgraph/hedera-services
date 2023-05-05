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

package com.hedera.node.app.records.files;

import static com.hedera.node.app.records.files.RecordFileFormatV6.HASH_HEADER;
import static com.hedera.node.app.records.files.RecordTestData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.streams.*;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.DigestType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("DataFlowIssue")
public class RecordStreamV6Test {
    private static final HexFormat HEX = HexFormat.of();

    public static Stream<Arguments> provideRecordStreamItems() {
        return Stream.of(
                Arguments.of(TEST_BLOCKS.get(0), true),
                Arguments.of(TEST_BLOCKS.get(1), false),
                Arguments.of(TEST_BLOCKS.get(2), true),
                Arguments.of(TEST_BLOCKS.get(3), false));
    }

    private @TempDir Path tempDir;

    @ParameterizedTest
    @MethodSource("provideRecordStreamItems")
    public void serializeTest(final List<SingleTransactionRecord> singleTransactionRecords, final boolean compress)
            throws Exception {
        MessageDigest messageDigest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
        final String compressionExt = compress ? ".gz" : "";
        final Path recordFilePath = tempDir.resolve("test.rcd" + compressionExt);
        try (final RecordFileWriter recordFileWriterV6 =
                new RecordFileWriter(recordFilePath, RecordFileFormatV6.INSTANCE, compress, BLOCK_NUM)) {
            Path sidecarPath = tempDir.resolve("test_01.rcd" + compressionExt);
            try (final SidecarFileWriter sidecarFileWriter = new SidecarFileWriter(sidecarPath, compress, 256)) {
                // write header
                recordFileWriterV6.writeHeader(VERSION, STARTING_RUNNING_HASH_OBJ);
                // serialize and write items
                final List<SerializedSingleTransactionRecord> serializedSingleTransactionRecords = new ArrayList<>();
                for (SingleTransactionRecord singleTransactionRecord : singleTransactionRecords) {
                    // serialize
                    final SerializedSingleTransactionRecord item =
                            RecordFileFormatV6.INSTANCE.serialize(singleTransactionRecord, BLOCK_NUM, VERSION);
                    serializedSingleTransactionRecords.add(item);
                    // write
                    recordFileWriterV6.writeRecordStreamItem(item);
                    // check serialize data protobuf parses back to same data
                    final RecordStreamItem parsedRecordStreamItem = RecordStreamItem.PROTOBUF.parse(BufferedData.wrap(
                            item.protobufSerializedRecordStreamItem().toByteArray()));
                    assertEquals(
                            new RecordStreamItem(
                                    singleTransactionRecord.transaction(), singleTransactionRecord.record()),
                            parsedRecordStreamItem);
                    // check sidecar items
                    assertEquals(
                            singleTransactionRecord.transactionSidecarRecords().size(),
                            item.sideCarItems().size());
                    assertEquals(
                            singleTransactionRecord.transactionSidecarRecords().size(),
                            item.sideCarItemsBytes().size());
                    // spotless:off
                    for (int i = 0; i < singleTransactionRecord.transactionSidecarRecords().size(); i++) {
                        // spotless:on
                        final TransactionSidecarRecord sideCarRecord = singleTransactionRecord
                                .transactionSidecarRecords()
                                .get(i);
                        final TransactionSidecarRecord sideCarRecord2 =
                                item.sideCarItems().get(i);
                        assertEquals(sideCarRecord, sideCarRecord2);
                        final Bytes sideCarRecordBytes =
                                item.sideCarItemsBytes().get(i);
                        assertEquals(
                                TransactionSidecarRecord.PROTOBUF
                                        .toBytes(sideCarRecord)
                                        .toHex(),
                                sideCarRecordBytes.toHex());
                        // write sidecar item
                        sidecarFileWriter.writeTransactionSidecarRecord(
                                sideCarRecord.sidecarRecords().kind(), sideCarRecordBytes);
                    }
                }
                // close sidecar file
                sidecarFileWriter.close();

                // computeNewRunningHash
                final Bytes endRunningHash = RecordFileFormatV6.INSTANCE.computeNewRunningHash(
                        STARTING_RUNNING_HASH_OBJ.hash(), serializedSingleTransactionRecords);
                // check running hash
                messageDigest.reset();
                byte[] previousHash = STARTING_RUNNING_HASH.getValue();
                for (final SerializedSingleTransactionRecord serializedItem : serializedSingleTransactionRecords) {
                    serializedItem.hashSerializedRecordStreamItem().writeTo(messageDigest);
                    final byte[] serializedItemHash = messageDigest.digest();
                    // now hash the previous hash and the item hash
                    messageDigest.update(HASH_HEADER);
                    messageDigest.update(previousHash);
                    messageDigest.update(HASH_HEADER);
                    messageDigest.update(serializedItemHash);
                    previousHash = messageDigest.digest();
                }
                assertEquals(HexFormat.of().formatHex(previousHash), endRunningHash.toHex());
                final HashObject endRunningHashObj = new HashObject(HashAlgorithm.SHA_384, 48, endRunningHash);
                // compute sidecar metadata
                final SidecarMetadata sidecarMetadata = new SidecarMetadata(
                        new HashObject(HashAlgorithm.SHA_384, 48, sidecarFileWriter.fileHash()),
                        1,
                        sidecarFileWriter.types());
                // write footer
                recordFileWriterV6.writeFooter(endRunningHashObj, List.of(sidecarMetadata));
                // close record file
                recordFileWriterV6.close();
                // compute hash of written file
                messageDigest.reset();
                byte[] fileHash = compress
                        ? messageDigest.digest(new GZIPInputStream(Files.newInputStream(recordFilePath)).readAllBytes())
                        : messageDigest.digest(Files.readAllBytes(recordFilePath));

                // check record file writer get methods
                assertEquals(
                        HEX.formatHex(previousHash),
                        recordFileWriterV6.endObjectRunningHash().hash().toHex());
                assertEquals(
                        HEX.formatHex(fileHash),
                        recordFileWriterV6.uncompressedFileHash().toHex());
                assertEquals(recordFilePath, recordFileWriterV6.filePath());

                // read written file and validate hashes
                RecordStreamFile readRecordStreamFile = RecordFileReaderV6.read(recordFilePath);
                assertEquals(VERSION, readRecordStreamFile.hapiProtoVersion());
                assertEquals(BLOCK_NUM, readRecordStreamFile.blockNumber());
                assertEquals(STARTING_RUNNING_HASH_OBJ, readRecordStreamFile.startObjectRunningHash());
                for (int i = 0; i < singleTransactionRecords.size(); i++) {
                    final SingleTransactionRecord singleTransactionRecord = singleTransactionRecords.get(i);
                    final RecordStreamItem recordStreamItem =
                            readRecordStreamFile.recordStreamItems().get(i);
                    assertEquals(singleTransactionRecord.transaction(), recordStreamItem.transaction());
                    assertEquals(singleTransactionRecord.record(), recordStreamItem.record());
                }
                assertEquals(endRunningHashObj, readRecordStreamFile.endObjectRunningHash());
                RecordFileReaderV6.validateHashes(readRecordStreamFile);
            }
        }
    }
}
