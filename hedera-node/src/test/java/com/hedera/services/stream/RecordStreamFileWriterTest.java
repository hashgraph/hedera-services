/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.stream;

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.generateStreamFileNameFromInstant;
import static com.swirlds.common.stream.StreamAligned.NO_ALIGNMENT;
import static com.swirlds.common.utility.Units.MB_TO_BYTES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.recordstreaming.RecordStreamingUtils;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.stream.proto.ContractActions;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.HashAlgorithm;
import com.hedera.services.stream.proto.HashObject;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.SidecarType;
import com.hedera.services.stream.proto.SignatureType;
import com.hedera.services.stream.proto.StorageChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hedera.services.stream.proto.TransactionSidecarRecord.Builder;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.TestFileUtils;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.stream.Signer;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class RecordStreamFileWriterTest {
    RecordStreamFileWriterTest() {}

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        subject =
                new RecordStreamFileWriter(
                        expectedExportDir(),
                        logPeriodMs,
                        signer,
                        false,
                        streamType,
                        expectedExportDir(),
                        maxSidecarFileSize,
                        globalDynamicProperties);
        messageDigest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
        messageDigest.digest("yumyum".getBytes(StandardCharsets.UTF_8));
        final var startRunningHash = new Hash(messageDigest.digest());
        subject.setRunningHash(startRunningHash);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void recordSignatureAndSidecarFilesAreCreatedAsExpected(final boolean isCompressed)
            throws IOException, NoSuchAlgorithmException {
        // given
        given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
        given(streamType.getSigFileHeader()).willReturn(SIG_FILE_HEADER_VALUES);
        given(streamType.getExtension()).willReturn(RecordStreamType.RECORD_EXTENSION);
        given(streamType.getSidecarExtension())
                .willReturn(RecordStreamType.SIDECAR_RECORD_EXTENSION);
        given(globalDynamicProperties.shouldCompressRecordFilesOnCreation())
                .willReturn(isCompressed);

        final var firstBlockEntireFileSignature =
                "entireSignatureBlock1".getBytes(StandardCharsets.UTF_8);
        final var firstBlockMetadataSignature =
                "metadataSignatureBlock1".getBytes(StandardCharsets.UTF_8);
        final var secondBlockEntireFileSignature =
                "entireSignatureBlock2".getBytes(StandardCharsets.UTF_8);
        final var secondBlockMetadataSignature =
                "metadataSignatureBlock2".getBytes(StandardCharsets.UTF_8);
        given(signer.sign(any()))
                .willReturn(firstBlockEntireFileSignature)
                .willReturn(firstBlockMetadataSignature)
                .willReturn(secondBlockEntireFileSignature)
                .willReturn(secondBlockMetadataSignature);
        final var firstTransactionInstant =
                LocalDateTime.of(2022, 5, 26, 11, 2, 55).toInstant(ZoneOffset.UTC);
        // set initial running hash
        messageDigest.digest("yumyum".getBytes(StandardCharsets.UTF_8));
        final var startRunningHash = new Hash(messageDigest.digest());
        subject.setRunningHash(startRunningHash);

        // when
        final int numberOfRSOsInFirstBlock = 4;
        final var firstBlockRSOs =
                generateNRecordStreamObjectsForBlockMStartingFromT(
                        numberOfRSOsInFirstBlock, 1, firstTransactionInstant, allSidecarTypes);
        final int numberOfRSOsInSecondBlock = 8;
        final var secondBlockRSOs =
                generateNRecordStreamObjectsForBlockMStartingFromT(
                        numberOfRSOsInSecondBlock,
                        2,
                        firstTransactionInstant.plusSeconds(logPeriodMs / 1000),
                        someSidecarTypes);
        final var thirdBlockRSOs =
                generateNRecordStreamObjectsForBlockMStartingFromT(
                        1,
                        3,
                        firstTransactionInstant.plusSeconds(2 * logPeriodMs / 1000),
                        Collections.emptyList());
        Stream.of(firstBlockRSOs, secondBlockRSOs, thirdBlockRSOs)
                .flatMap(Collection::stream)
                .forEach(subject::addObject);

        // then
        assertRecordStreamFiles(
                1L,
                firstBlockRSOs,
                startRunningHash,
                firstBlockEntireFileSignature,
                firstBlockMetadataSignature,
                Map.of(1, allSidecarTypesEnum),
                Map.of(1, transformToExpectedSidecars(allSidecarTypes, numberOfRSOsInFirstBlock)),
                isCompressed);
        assertRecordStreamFiles(
                2L,
                secondBlockRSOs,
                firstBlockRSOs.get(firstBlockRSOs.size() - 1).getRunningHash().getHash(),
                secondBlockEntireFileSignature,
                secondBlockMetadataSignature,
                Map.of(1, someSidecarTypesEnum),
                Map.of(1, transformToExpectedSidecars(someSidecarTypes, numberOfRSOsInSecondBlock)),
                isCompressed);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void objectsFromFirstPeriodAreNotExternalizedWhenStartWriteAtCompleteWindowIsTrue(
            final boolean isCompressed) throws IOException, NoSuchAlgorithmException {
        // given
        given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
        given(streamType.getSigFileHeader()).willReturn(SIG_FILE_HEADER_VALUES);
        given(streamType.getExtension()).willReturn(RecordStreamType.RECORD_EXTENSION);
        given(globalDynamicProperties.shouldCompressRecordFilesOnCreation())
                .willReturn(isCompressed);
        final var secondBlockEntireFileSignature =
                "entireSignatureBlock2".getBytes(StandardCharsets.UTF_8);
        final var secondBlockMetadataSignature =
                "metadataSignatureBlock2".getBytes(StandardCharsets.UTF_8);
        given(signer.sign(any()))
                .willReturn(secondBlockEntireFileSignature)
                .willReturn(secondBlockMetadataSignature);
        final var firstTransactionInstant =
                LocalDateTime.of(2022, 5, 24, 11, 2, 55).toInstant(ZoneOffset.UTC);
        // set initial running hash
        messageDigest.digest("yumyum".getBytes(StandardCharsets.UTF_8));
        final var startRunningHash = new Hash(messageDigest.digest());
        subject.setRunningHash(startRunningHash);
        subject.setStartWriteAtCompleteWindow(true);

        // when
        final var firstBlockRSOs =
                generateNRecordStreamObjectsForBlockMStartingFromT(
                        1, 1, firstTransactionInstant, allSidecarTypes);
        final int numberOfRSOsInSecondBlock = 5;
        final var secondBlockRSOs =
                generateNRecordStreamObjectsForBlockMStartingFromT(
                        numberOfRSOsInSecondBlock,
                        2,
                        firstTransactionInstant.plusSeconds(logPeriodMs / 1000),
                        allSidecarTypes);
        final var thirdBlockRSOs =
                generateNRecordStreamObjectsForBlockMStartingFromT(
                        1,
                        3,
                        firstTransactionInstant.plusSeconds(2 * logPeriodMs / 1000),
                        allSidecarTypes);
        Stream.of(firstBlockRSOs, secondBlockRSOs, thirdBlockRSOs)
                .flatMap(Collection::stream)
                .forEach(subject::addObject);

        // then
        assertFalse(
                Path.of(subject.generateRecordFilePath(firstTransactionInstant)).toFile().exists());
        assertRecordStreamFiles(
                2L,
                secondBlockRSOs,
                firstBlockRSOs.get(firstBlockRSOs.size() - 1).getRunningHash().getHash(),
                secondBlockEntireFileSignature,
                secondBlockMetadataSignature,
                Map.of(1, allSidecarTypesEnum),
                Map.of(1, transformToExpectedSidecars(allSidecarTypes, numberOfRSOsInSecondBlock)),
                isCompressed);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void objectsFromDifferentPeriodsButWithSameAlignmentAreExternalizedInSameFile(
            final boolean isCompressed) throws IOException, NoSuchAlgorithmException {
        // given
        given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
        given(streamType.getSigFileHeader()).willReturn(SIG_FILE_HEADER_VALUES);
        given(streamType.getExtension()).willReturn(RecordStreamType.RECORD_EXTENSION);
        given(globalDynamicProperties.shouldCompressRecordFilesOnCreation())
                .willReturn(isCompressed);
        final var firstBlockEntireFileSignature =
                "entireSignatureBlock1".getBytes(StandardCharsets.UTF_8);
        final var firstBlockMetadataSignature =
                "metadataSignatureBlock1".getBytes(StandardCharsets.UTF_8);
        given(signer.sign(any()))
                .willReturn(firstBlockEntireFileSignature)
                .willReturn(firstBlockMetadataSignature);
        final var firstTransactionInstant =
                LocalDateTime.of(2022, 9, 24, 11, 2, 55).toInstant(ZoneOffset.UTC);
        // set initial running hash
        messageDigest.digest("yumyum".getBytes(StandardCharsets.UTF_8));
        final var startRunningHash = new Hash(messageDigest.digest());
        subject.setRunningHash(startRunningHash);

        // when
        // generate 2 RSOs for block #1, where the second RSO is in different period, but with same
        // alignment (block)
        final var numberOfRSOsInFirstBlock = 1;
        final var firstBlockRSOs =
                generateNRecordStreamObjectsForBlockMStartingFromT(
                        numberOfRSOsInFirstBlock, 1, firstTransactionInstant, allSidecarTypes);
        firstBlockRSOs.addAll(
                generateNRecordStreamObjectsForBlockMStartingFromT(
                        1,
                        1,
                        firstTransactionInstant.plusSeconds(2 * (logPeriodMs / 1000)),
                        allSidecarTypes));
        // RSOs for second block to trigger externalization of first block
        final var secondBlockRSOs =
                generateNRecordStreamObjectsForBlockMStartingFromT(
                        1,
                        2,
                        firstTransactionInstant.plusSeconds(3 * (logPeriodMs / 1000)),
                        allSidecarTypes);
        Stream.of(firstBlockRSOs, secondBlockRSOs)
                .flatMap(Collection::stream)
                .forEach(subject::addObject);

        // then
        assertRecordStreamFiles(
                1L,
                firstBlockRSOs,
                startRunningHash,
                firstBlockEntireFileSignature,
                firstBlockMetadataSignature,
                Map.of(1, allSidecarTypesEnum),
                Map.of(1, transformToExpectedSidecars(allSidecarTypes, numberOfRSOsInFirstBlock)),
                isCompressed);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void alignmentIsIgnoredForObjectsWithNoAlignment(final boolean isCompressed)
            throws IOException, NoSuchAlgorithmException {
        // given
        given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
        given(streamType.getSigFileHeader()).willReturn(SIG_FILE_HEADER_VALUES);
        given(streamType.getExtension()).willReturn(RecordStreamType.RECORD_EXTENSION);
        given(globalDynamicProperties.shouldCompressRecordFilesOnCreation())
                .willReturn(isCompressed);
        final var firstBlockEntireFileSignature =
                "entireSignatureBlock1".getBytes(StandardCharsets.UTF_8);
        final var firstBlockMetadataSignature =
                "metadataSignatureBlock1".getBytes(StandardCharsets.UTF_8);
        given(signer.sign(any()))
                .willReturn(firstBlockEntireFileSignature)
                .willReturn(firstBlockMetadataSignature);
        final var firstTransactionInstant =
                LocalDateTime.of(2022, 10, 24, 16, 2, 55).toInstant(ZoneOffset.UTC);
        // set initial running hash
        messageDigest.digest("yumyum".getBytes(StandardCharsets.UTF_8));
        final var startRunningHash = new Hash(messageDigest.digest());
        subject.setRunningHash(startRunningHash);

        // when
        // generate 2 RSOs for block #1 without alignment; should be externalized in same record
        // file
        final var numberOfRSOsInFirstBlock = 2;
        final var firstBlockRSOs =
                generateNRecordStreamObjectsForBlockMStartingFromT(
                        numberOfRSOsInFirstBlock,
                        NO_ALIGNMENT,
                        firstTransactionInstant,
                        allSidecarTypes);
        // generate 1 RSO in next block to trigger externalization of previous file; even though
        // alignments are equal,
        // when they are NO_ALIGNMENT, we ignore it and start a new file regardless
        final var secondBlockRSOs =
                generateNRecordStreamObjectsForBlockMStartingFromT(
                        1,
                        NO_ALIGNMENT,
                        firstTransactionInstant.plusSeconds(4 * (logPeriodMs / 1000)),
                        allSidecarTypes);
        Stream.of(firstBlockRSOs, secondBlockRSOs)
                .flatMap(Collection::stream)
                .forEach(subject::addObject);

        // then
        assertRecordStreamFiles(
                NO_ALIGNMENT,
                firstBlockRSOs,
                startRunningHash,
                firstBlockEntireFileSignature,
                firstBlockMetadataSignature,
                Map.of(1, allSidecarTypesEnum),
                Map.of(1, transformToExpectedSidecars(allSidecarTypes, numberOfRSOsInFirstBlock)),
                isCompressed);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sidecarFileSizeLimitIsRespected(final boolean isCompressed)
            throws IOException, NoSuchAlgorithmException {
        // given
        given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
        given(streamType.getSigFileHeader()).willReturn(SIG_FILE_HEADER_VALUES);
        given(streamType.getExtension()).willReturn(RecordStreamType.RECORD_EXTENSION);
        given(streamType.getSidecarExtension())
                .willReturn(RecordStreamType.SIDECAR_RECORD_EXTENSION);
        given(globalDynamicProperties.shouldCompressRecordFilesOnCreation())
                .willReturn(isCompressed);
        final var firstBlockEntireFileSignature =
                "entireSignatureBlock1".getBytes(StandardCharsets.UTF_8);
        final var firstBlockMetadataSignature =
                "metadataSignatureBlock1".getBytes(StandardCharsets.UTF_8);
        given(signer.sign(any()))
                .willReturn(firstBlockEntireFileSignature)
                .willReturn(firstBlockMetadataSignature);
        final var firstTransactionInstant =
                LocalDateTime.of(2022, 7, 21, 15, 58, 55).toInstant(ZoneOffset.UTC);
        // set initial running hash
        messageDigest.digest("yumyum".getBytes(StandardCharsets.UTF_8));
        final var startRunningHash = new Hash(messageDigest.digest());
        subject.setRunningHash(startRunningHash);

        final var bigBytecode =
                ContractBytecode.newBuilder()
                        .setInitcode(ByteString.copyFrom(new byte[maxSidecarFileSize - 50]))
                        .build();
        final var bigStateChange =
                ContractStateChanges.newBuilder()
                        .addContractStateChanges(
                                ContractStateChange.newBuilder()
                                        .addStorageChanges(
                                                StorageChange.newBuilder()
                                                        .setValueRead(
                                                                ByteString.copyFrom(
                                                                        new byte
                                                                                [maxSidecarFileSize
                                                                                        - 50]))
                                                        .build())
                                        .build())
                        .build();
        final var bigSidecar1 = TransactionSidecarRecord.newBuilder().setBytecode(bigBytecode);
        final var bigSidecar2 =
                TransactionSidecarRecord.newBuilder().setStateChanges(bigStateChange);

        // when
        final var firstBlockRSOs =
                generateNRecordStreamObjectsForBlockMStartingFromT(
                        1, 1, firstTransactionInstant, List.of(bigSidecar1, bigSidecar2));
        final var secondBlockRSOs =
                generateNRecordStreamObjectsForBlockMStartingFromT(
                        1,
                        3,
                        firstTransactionInstant.plusSeconds(2 * logPeriodMs / 1000),
                        Collections.emptyList());
        Stream.of(firstBlockRSOs, secondBlockRSOs)
                .flatMap(Collection::stream)
                .forEach(subject::addObject);

        // then
        final var sidecarIdToExpectedContainedSidecars =
                Map.of(
                        1, List.of(bigSidecar1),
                        2, List.of(bigSidecar2));
        assertRecordStreamFiles(
                1L,
                firstBlockRSOs,
                startRunningHash,
                firstBlockEntireFileSignature,
                firstBlockMetadataSignature,
                Map.of(
                        1,
                        EnumSet.of(SidecarType.CONTRACT_BYTECODE),
                        2,
                        EnumSet.of(SidecarType.CONTRACT_STATE_CHANGE)),
                sidecarIdToExpectedContainedSidecars,
                isCompressed);
    }

    private List<RecordStreamObject> generateNRecordStreamObjectsForBlockMStartingFromT(
            final int numberOfRSOs,
            final long blockNumber,
            final Instant firstBlockTransactionInstant,
            final List<TransactionSidecarRecord.Builder> sidecarRecords) {
        final var recordStreamObjects = new ArrayList<RecordStreamObject>();
        for (int i = 0; i < numberOfRSOs; i++) {
            final var timestamp =
                    Timestamp.newBuilder()
                            .setSeconds(firstBlockTransactionInstant.getEpochSecond())
                            .setNanos(1000 * i);
            final var expirableBuilder =
                    ExpirableTxnRecord.newBuilder()
                            .setConsensusTime(
                                    RichInstant.fromJava(
                                            Instant.ofEpochSecond(
                                                    timestamp.getSeconds(), timestamp.getNanos())));
            final var transaction =
                    Transaction.newBuilder()
                            .setSignedTransactionBytes(
                                    ByteString.copyFrom(
                                            ("block #" + blockNumber + ", transaction #" + i)
                                                    .getBytes(StandardCharsets.UTF_8)));
            final var recordStreamObject =
                    new RecordStreamObject(
                            expirableBuilder.build(),
                            transaction.build(),
                            Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()),
                            sidecarRecords);
            final var hashInput = recordStreamObject.toString().getBytes(StandardCharsets.UTF_8);
            recordStreamObject.getRunningHash().setHash(new Hash(messageDigest.digest(hashInput)));
            recordStreamObject.withBlockNumber(blockNumber);
            recordStreamObjects.add(recordStreamObject);
        }
        return recordStreamObjects;
    }

    private void assertRecordStreamFiles(
            final long expectedBlock,
            final List<RecordStreamObject> blockRSOs,
            final Hash startRunningHash,
            final byte[] expectedEntireFileSignature,
            final byte[] expectedMetadataSignature,
            final Map<Integer, EnumSet<SidecarType>> sidecarIdToExpectedSidecarTypes,
            final Map<Integer, List<Builder>> sidecarIdToExpectedSidecars,
            final boolean isCompressed)
            throws IOException, NoSuchAlgorithmException {
        final var firstTxnTimestamp = blockRSOs.get(0).getTimestamp();
        final var recordStreamFilePath =
                subject.generateRecordFilePath(
                        Instant.ofEpochSecond(
                                firstTxnTimestamp.getEpochSecond(), firstTxnTimestamp.getNano()));
        final var recordStreamFilePair =
                isCompressed
                        ? RecordStreamingUtils.readRecordStreamFile(
                                recordStreamFilePath + compressedExtension)
                        : RecordStreamingUtils.readUncompressedRecordStreamFile(
                                recordStreamFilePath);

        assertEquals(RECORD_STREAM_VERSION, recordStreamFilePair.getLeft());
        final var recordStreamFileOptional = recordStreamFilePair.getRight();
        assertTrue(recordStreamFileOptional.isPresent());
        final var recordStreamFile = recordStreamFileOptional.get();

        assertRecordFile(
                expectedBlock,
                blockRSOs,
                startRunningHash,
                recordStreamFile,
                new File(
                        isCompressed
                                ? recordStreamFilePath + compressedExtension
                                : recordStreamFilePath),
                sidecarIdToExpectedSidecarTypes,
                sidecarIdToExpectedSidecars,
                isCompressed);
        assertSignatureFile(
                recordStreamFilePath,
                expectedEntireFileSignature,
                expectedMetadataSignature,
                recordStreamFilePair.getLeft(),
                recordStreamFile);
    }

    private void assertRecordFile(
            final long expectedBlock,
            final List<RecordStreamObject> blockRSOs,
            final Hash startRunningHash,
            final RecordStreamFile recordStreamFile,
            final File recordFile,
            final Map<Integer, EnumSet<SidecarType>> sidecarIdToExpectedSidecarTypes,
            final Map<Integer, List<Builder>> sidecarIdToExpectedSidecars,
            final boolean isCompressed)
            throws IOException, NoSuchAlgorithmException {
        assertTrue(logCaptor.debugLogs().contains("Stream file created " + recordFile.getName()));

        // assert HAPI semantic version
        assertEquals(
                recordStreamFile.getHapiProtoVersion(),
                SemanticVersion.newBuilder()
                        .setMajor(FILE_HEADER_VALUES[1])
                        .setMinor(FILE_HEADER_VALUES[2])
                        .setPatch(FILE_HEADER_VALUES[3])
                        .build());

        // assert startRunningHash
        assertEquals(toProto(startRunningHash), recordStreamFile.getStartObjectRunningHash());

        assertTrue(
                logCaptor
                        .debugLogs()
                        .contains(
                                "beginNew :: write startRunningHash to metadata "
                                        + startRunningHash));

        // assert RSOs
        assertEquals(blockRSOs.size(), recordStreamFile.getRecordStreamItemsCount());
        final var recordFileObjectsList = recordStreamFile.getRecordStreamItemsList();
        for (int i = 0; i < blockRSOs.size(); i++) {
            final var expectedRSO = blockRSOs.get(i);
            final var actualRSOProto = recordFileObjectsList.get(i);
            assertEquals(expectedRSO.getTransaction(), actualRSOProto.getTransaction());
            assertEquals(expectedRSO.getTransactionRecord(), actualRSOProto.getRecord());
        }

        // assert endRunningHash - should be the hash of the last RSO from the block
        final var expectedHashInput =
                blockRSOs.get(blockRSOs.size() - 1).toString().getBytes(StandardCharsets.UTF_8);
        final var expectedEndRunningHash = new Hash(messageDigest.digest(expectedHashInput));
        assertEquals(toProto(expectedEndRunningHash), recordStreamFile.getEndObjectRunningHash());

        assertTrue(
                logCaptor
                        .debugLogs()
                        .contains(
                                "closeCurrentAndSign :: write endRunningHash "
                                        + expectedEndRunningHash));

        // assert block number
        assertEquals(expectedBlock, recordStreamFile.getBlockNumber());
        assertTrue(
                logCaptor
                        .debugLogs()
                        .contains("closeCurrentAndSign :: write block number " + expectedBlock));

        // assert sidecar metadata
        final var firstTxnTimestamp = blockRSOs.get(0).getTimestamp();
        final var firstTxnInstant =
                Instant.ofEpochSecond(
                        firstTxnTimestamp.getEpochSecond(), firstTxnTimestamp.getNano());
        var sidecarId = 1;
        final var sidecarMetadataList = recordStreamFile.getSidecarsList();
        for (final var sidecarMetadata : sidecarMetadataList) {
            assertEquals(
                    sidecarIdToExpectedSidecarTypes.get(sidecarId).stream().toList(),
                    sidecarMetadata.getTypesList());
            final var pathToSidecarFile =
                    subject.generateSidecarFilePath(firstTxnInstant, sidecarId);
            final var sidecarFileProto =
                    isCompressed
                            ? RecordStreamingUtils.readSidecarFile(pathToSidecarFile)
                            : RecordStreamingUtils.readUncompressedSidecarFile(pathToSidecarFile);
            assertAllSidecarsAreInFile(
                    sidecarIdToExpectedSidecars.get(sidecarId),
                    sidecarFileProto.getSidecarRecordsList());
            final var sidecarFile = new File(pathToSidecarFile);
            assertFalse(sidecarFile.length() > maxSidecarFileSize);
            final var messageDigest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
            messageDigest.update(sidecarFileProto.toByteArray());
            final var actualSidecarHash = sidecarMetadata.getHash();
            assertEquals(HashAlgorithm.SHA_384, actualSidecarHash.getAlgorithm());
            assertEquals(messageDigest.getDigestLength(), actualSidecarHash.getLength());
            assertArrayEquals(messageDigest.digest(), actualSidecarHash.getHash().toByteArray());
            assertTrue(
                    logCaptor
                            .debugLogs()
                            .contains(
                                    "Sidecar file created successfully " + sidecarFile.getName()));
            sidecarId++;
        }

        assertTrue(
                logCaptor
                        .debugLogs()
                        .contains("Stream file written successfully " + recordFile.getName()));
    }

    private void assertAllSidecarsAreInFile(
            final List<TransactionSidecarRecord.Builder> expectedSidecars,
            final List<TransactionSidecarRecord> actualSidecars) {
        for (int i = 0; i < expectedSidecars.size(); i++) {
            assertEquals(expectedSidecars.get(i).build(), actualSidecars.get(i));
        }
    }

    private void assertSignatureFile(
            final String recordStreamFilePath,
            final byte[] expectedEntireFileSignature,
            final byte[] expectedMetadataSignature,
            final Integer recordStreamVersion,
            final RecordStreamFile recordStreamFileProto)
            throws IOException, NoSuchAlgorithmException {
        final var signatureFilePath = recordStreamFilePath + "_sig";
        final var signatureFilePair = RecordStreamingUtils.readSignatureFile(signatureFilePath);
        assertEquals(RECORD_STREAM_VERSION, signatureFilePair.getLeft());

        final var signatureFileOptional = signatureFilePair.getRight();
        assertTrue(signatureFileOptional.isPresent());
        final var signatureFile = signatureFileOptional.get();

        /* --- assert entire file signature --- */
        final var entireFileSignatureObject = signatureFile.getFileSignature();
        // assert entire file hash
        final var messageDigest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
        messageDigest.update(ByteBuffer.allocate(4).putInt(recordStreamVersion).array());
        messageDigest.update(recordStreamFileProto.toByteArray());
        final var actualEntireHash = entireFileSignatureObject.getHashObject();
        assertEquals(HashAlgorithm.SHA_384, actualEntireHash.getAlgorithm());
        assertEquals(messageDigest.getDigestLength(), actualEntireHash.getLength());
        assertArrayEquals(messageDigest.digest(), actualEntireHash.getHash().toByteArray());
        // assert entire file signature
        assertEquals(SignatureType.SHA_384_WITH_RSA, entireFileSignatureObject.getType());
        assertEquals(expectedEntireFileSignature.length, entireFileSignatureObject.getLength());
        assertEquals(
                101 - expectedEntireFileSignature.length, entireFileSignatureObject.getChecksum());
        assertArrayEquals(
                expectedEntireFileSignature,
                entireFileSignatureObject.getSignature().toByteArray());

        /* --- assert metadata signature --- */
        final var expectedMetaHash =
                computeMetadataHashFrom(recordStreamVersion, recordStreamFileProto);
        final var metadataSignatureObject = signatureFile.getMetadataSignature();
        final var actualMetaHash = metadataSignatureObject.getHashObject();
        // assert metadata hash
        assertEquals(HashAlgorithm.SHA_384, actualMetaHash.getAlgorithm());
        assertEquals(expectedMetaHash.getDigestType().digestLength(), actualMetaHash.getLength());
        assertArrayEquals(expectedMetaHash.getValue(), actualMetaHash.getHash().toByteArray());
        // assert metadata signature
        assertEquals(SignatureType.SHA_384_WITH_RSA, metadataSignatureObject.getType());
        assertEquals(expectedMetadataSignature.length, metadataSignatureObject.getLength());
        assertEquals(101 - expectedMetadataSignature.length, metadataSignatureObject.getChecksum());
        assertArrayEquals(
                expectedMetadataSignature, metadataSignatureObject.getSignature().toByteArray());

        assertTrue(
                logCaptor
                        .debugLogs()
                        .contains(
                                "closeCurrentAndSign :: signature file saved: "
                                        + signatureFilePath));
    }

    private HashObject toProto(final Hash hash) {
        return HashObject.newBuilder()
                .setAlgorithm(HashAlgorithm.SHA_384)
                .setLength(hash.getDigestType().digestLength())
                .setHash(ByteString.copyFrom(hash.getValue()))
                .build();
    }

    private Hash computeMetadataHashFrom(
            final Integer version, final RecordStreamFile recordStreamFile) {
        try (final var outputStream =
                new SerializableDataOutputStream(new HashingOutputStream(messageDigest))) {
            // digest file header
            outputStream.writeInt(version);
            final var hapiProtoVersion = recordStreamFile.getHapiProtoVersion();
            outputStream.writeInt(hapiProtoVersion.getMajor());
            outputStream.writeInt(hapiProtoVersion.getMinor());
            outputStream.writeInt(hapiProtoVersion.getPatch());

            // digest startRunningHash
            final var startRunningHash =
                    new Hash(
                            recordStreamFile.getStartObjectRunningHash().getHash().toByteArray(),
                            DigestType.SHA_384);
            outputStream.write(startRunningHash.getValue());

            // digest endRunningHash
            final var endRunningHash =
                    new Hash(
                            recordStreamFile.getEndObjectRunningHash().getHash().toByteArray(),
                            DigestType.SHA_384);
            outputStream.write(endRunningHash.getValue());

            // digest block number
            outputStream.writeLong(recordStreamFile.getBlockNumber());

            return new Hash(messageDigest.digest(), DigestType.SHA_384);
        } catch (IOException e) {
            return new Hash("error".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void clearCalledInMiddleOfWritingRecordFileSucceeds() {
        // given
        given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
        final var firstTransactionInstant =
                LocalDateTime.of(2022, 5, 24, 11, 2, 55).toInstant(ZoneOffset.UTC);
        // send RSOs for block 1
        final var firstBlockRSOs =
                generateNRecordStreamObjectsForBlockMStartingFromT(
                        4, 1, firstTransactionInstant, allSidecarTypes);
        firstBlockRSOs.forEach(subject::addObject);

        // when
        subject.clear();

        // then
        assertTrue(logCaptor.debugLogs().contains("RecordStreamFileWriter::clear executed."));
    }

    @Test
    void clearCalledWhenNotWritingFileSucceeds() {
        // when
        subject.clear();

        // then
        assertThat(
                logCaptor.debugLogs(),
                contains(Matchers.startsWith("RecordStreamFileWriter::clear executed.")));
    }

    @Test
    void clearCatchesIOExceptionWhenClosingStreamsAndLogsIt() {
        try (final var ignored =
                Mockito.mockConstruction(
                        SerializableDataOutputStream.class,
                        (mock, context) -> doThrow(IOException.class).when(mock).close())) {
            // given
            given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
            final var firstTransactionInstant =
                    LocalDateTime.of(2022, 5, 24, 11, 2, 55).toInstant(ZoneOffset.UTC);
            // send RSOs for block 1
            generateNRecordStreamObjectsForBlockMStartingFromT(
                            1, 1, firstTransactionInstant, allSidecarTypes)
                    .forEach(subject::addObject);

            // when
            subject.clear();

            // then
            assertThat(
                    logCaptor.warnLogs(),
                    contains(
                            Matchers.startsWith(
                                    "RecordStreamFileWriter::clear Exception in closing dosMeta")));
            assertTrue(logCaptor.debugLogs().contains("RecordStreamFileWriter::clear executed."));
        }
    }

    @Test
    void closeSucceeds() {
        // given
        final var subjectSpy = Mockito.spy(subject);

        // when
        subjectSpy.close();

        // then
        verify(subjectSpy).closeCurrentAndSign();
        assertThat(
                logCaptor.debugLogs(),
                contains(
                        Matchers.startsWith(
                                "RecordStreamFileWriter finished writing the last object, is"
                                        + " stopped")));
    }

    @Test
    void writingBlockNumberToMetadataIOEExceptionIsCaughtAndLoggedProperlyAndThreadInterrupted() {
        // given
        given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
        final var firstTransactionInstant =
                LocalDateTime.of(2022, 5, 24, 11, 2, 55).toInstant(ZoneOffset.UTC);

        // when
        try (MockedConstruction<SerializableDataOutputStream> ignored =
                Mockito.mockConstruction(
                        SerializableDataOutputStream.class,
                        (mock, context) ->
                                doThrow(IOException.class).when(mock).writeLong(anyLong()))) {
            sendRSOsForBlock1And2StartingFrom(firstTransactionInstant);
        }

        // then
        assertTrue(Thread.currentThread().isInterrupted());
        assertThat(
                logCaptor.warnLogs(),
                contains(
                        Matchers.startsWith(
                                "closeCurrentAndSign :: IOException when serializing endRunningHash"
                                        + " and block number into metadata")));
    }

    @Test
    void logAndDontDoAnythingWhenStreamFileAlreadyExists() throws IOException {
        // given
        given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
        given(globalDynamicProperties.shouldCompressRecordFilesOnCreation()).willReturn(true);
        final var firstTransactionInstant =
                LocalDateTime.of(2022, 1, 24, 11, 2, 55).toInstant(ZoneOffset.UTC);
        final var expectedRecordFileName =
                generateStreamFileNameFromInstant(firstTransactionInstant, streamType)
                        + compressedExtension;
        final var recordFile =
                new File(expectedExportDir() + File.separator + expectedRecordFileName)
                        .createNewFile();
        assertTrue(recordFile);

        // when
        sendRSOsForBlock1And2StartingFrom(firstTransactionInstant);

        // then
        assertTrue(
                logCaptor
                        .debugLogs()
                        .contains("Stream file already exists " + expectedRecordFileName));
    }

    private void sendRSOsForBlock1And2StartingFrom(Instant firstTransactionInstant) {
        final var firstBlockRSOs =
                generateNRecordStreamObjectsForBlockMStartingFromT(
                        1, 1, firstTransactionInstant, allSidecarTypes);
        final var secondBlockRSOs =
                generateNRecordStreamObjectsForBlockMStartingFromT(
                        1,
                        2,
                        firstTransactionInstant.plusSeconds(2 * logPeriodMs / 1000),
                        allSidecarTypes);

        // when
        Stream.of(firstBlockRSOs, secondBlockRSOs)
                .flatMap(Collection::stream)
                .forEach(subject::addObject);
    }

    @Test
    void interruptThreadAndLogWhenIOExceptionIsCaughtWhileWritingSidecarRecordFile() {
        given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
        given(streamType.getExtension()).willReturn(RecordStreamType.RECORD_EXTENSION);
        given(streamType.getSidecarExtension())
                .willReturn(RecordStreamType.SIDECAR_RECORD_EXTENSION);
        final var firstTransactionInstant =
                LocalDateTime.of(2022, 11, 13, 11, 1, 55).toInstant(ZoneOffset.UTC);
        final var firstBlockRSOs =
                generateNRecordStreamObjectsForBlockMStartingFromT(
                        1, 1, firstTransactionInstant, allSidecarTypes);
        firstBlockRSOs.forEach(subject::addObject);

        try (MockedConstruction<SerializableDataOutputStream> ignored =
                Mockito.mockConstruction(
                        SerializableDataOutputStream.class,
                        (mock, context) -> doThrow(IOException.class).when(mock).write(any()))) {
            subject.closeCurrentAndSign();
            assertTrue(Thread.currentThread().isInterrupted());
            assertThat(
                    logCaptor.warnLogs(),
                    contains(
                            Matchers.startsWith(
                                    "closeCurrentAndSign :: IOException when creating sidecar"
                                            + " files")));
        }
    }

    @Test
    void interruptThreadAndLogWhenIOExceptionIsCaughtWhileWritingRecordFile() {
        given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
        final var firstTransactionInstant =
                LocalDateTime.of(2022, 1, 3, 21, 2, 55).toInstant(ZoneOffset.UTC);
        final var firstBlockRSOs =
                generateNRecordStreamObjectsForBlockMStartingFromT(
                        1, 1, firstTransactionInstant, Collections.emptyList());
        firstBlockRSOs.forEach(subject::addObject);

        try (MockedConstruction<SerializableDataOutputStream> ignored =
                Mockito.mockConstruction(
                        SerializableDataOutputStream.class,
                        (mock, context) ->
                                doThrow(IOException.class).when(mock).writeInt(anyInt()))) {
            subject.closeCurrentAndSign();
            assertTrue(Thread.currentThread().isInterrupted());
            assertThat(
                    logCaptor.warnLogs(),
                    contains(
                            Matchers.startsWith(
                                    "closeCurrentAndSign :: IOException when serializing ")));
        }
    }

    @Test
    void interruptThreadAndLogWhenIOExceptionIsCaughtWhileWritingSidecarInConsume() {
        // given
        given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
        given(streamType.getSidecarExtension())
                .willReturn(RecordStreamType.SIDECAR_RECORD_EXTENSION);
        final var firstTransactionInstant =
                LocalDateTime.of(2022, 7, 21, 15, 59, 55).toInstant(ZoneOffset.UTC);
        final var bigBytecode =
                ContractBytecode.newBuilder()
                        .setInitcode(ByteString.copyFrom(new byte[maxSidecarFileSize - 50]))
                        .build();
        final var bigSidecar1 = TransactionSidecarRecord.newBuilder().setBytecode(bigBytecode);
        final var bigSidecar2 = TransactionSidecarRecord.newBuilder().setBytecode(bigBytecode);
        final var firstBlockRSOs =
                generateNRecordStreamObjectsForBlockMStartingFromT(
                        1, 1, firstTransactionInstant, List.of(bigSidecar1, bigSidecar2));
        try (MockedConstruction<SerializableDataOutputStream> ignored =
                Mockito.mockConstruction(
                        SerializableDataOutputStream.class,
                        (mock, context) -> doThrow(IOException.class).when(mock).write(any()))) {

            // when
            firstBlockRSOs.forEach(subject::addObject);

            // then
            assertTrue(Thread.currentThread().isInterrupted());
            assertThat(
                    logCaptor.warnLogs(),
                    contains(
                            Matchers.startsWith(
                                    "consume :: IOException when creating sidecar files")));
        }
    }

    @Test
    void waitingForStartRunningHashInterruptedExceptionIsCaughtAndLoggedProperly() {
        // given
        given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
        final var firstTransactionInstant =
                LocalDateTime.of(2022, 4, 29, 11, 2, 55).toInstant(ZoneOffset.UTC);
        subject.clearRunningHash();

        // when
        Thread.currentThread().interrupt();
        generateNRecordStreamObjectsForBlockMStartingFromT(
                        1, 1, firstTransactionInstant, allSidecarTypes)
                .forEach(subject::addObject);

        // then
        assertTrue(
                logCaptor
                        .errorLogs()
                        .get(0)
                        .startsWith(
                                "beginNew :: Exception when getting startRunningHash for writing to"
                                        + " metadata stream"));
    }

    @Test
    void waitingForEndRunningHashInterruptedExceptionIsCaughtAndLoggedProperly() {
        // given
        given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
        final var firstTransactionInstant =
                LocalDateTime.of(2022, 4, 29, 11, 2, 55).toInstant(ZoneOffset.UTC);
        generateNRecordStreamObjectsForBlockMStartingFromT(
                        1, 1, firstTransactionInstant, allSidecarTypes)
                .forEach(subject::addObject);

        // when
        Thread.currentThread().interrupt();
        subject.closeCurrentAndSign();

        // then
        assertTrue(
                logCaptor
                        .errorLogs()
                        .get(0)
                        .startsWith("closeCurrentAndSign :: failed when getting endRunningHash "));
    }

    @Test
    void exceptionWhenWritingSignatureFileIsCaughtAndLogged() {
        // given
        given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
        final var firstBlockEntireFileSignature =
                "entireSignatureBlock1".getBytes(StandardCharsets.UTF_8);
        final var firstBlockMetadataSignature =
                "metadataSignatureBlock1".getBytes(StandardCharsets.UTF_8);
        final var secondBlockEntireFileSignature =
                "entireSignatureBlock2".getBytes(StandardCharsets.UTF_8);
        final var secondBlockMetadataSignature =
                "metadataSignatureBlock2".getBytes(StandardCharsets.UTF_8);
        given(signer.sign(any()))
                .willReturn(firstBlockEntireFileSignature)
                .willReturn(firstBlockMetadataSignature)
                .willReturn(secondBlockEntireFileSignature)
                .willReturn(secondBlockMetadataSignature);
        final var firstTransactionInstant =
                LocalDateTime.of(2022, 5, 11, 16, 2, 55).toInstant(ZoneOffset.UTC);
        // bear in mind that IOException can't really be thrown from this invocation,
        // but this is the only way we can test the expected behavior
        given(streamType.getSigFileHeader())
                .willAnswer(
                        invocation -> {
                            throw new IOException();
                        });

        sendRSOsForBlock1And2StartingFrom(firstTransactionInstant);

        assertThat(
                logCaptor.errorLogs(),
                contains(
                        Matchers.startsWith(
                                "closeCurrentAndSign ::  :: Fail to "
                                        + "generate signature file for")));
    }

    @Test
    void interruptAndLogWhenWritingStartRunningHashToMetadataStreamThrowsIOException() {
        // given
        given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
        final var firstTransactionInstant =
                LocalDateTime.of(2022, 5, 24, 11, 2, 55).toInstant(ZoneOffset.UTC);

        // when
        try (MockedConstruction<SerializableDataOutputStream> ignored =
                Mockito.mockConstruction(
                        SerializableDataOutputStream.class,
                        (mock, context) -> doThrow(IOException.class).when(mock).write(any()))) {
            generateNRecordStreamObjectsForBlockMStartingFromT(
                            1, 1, firstTransactionInstant, Collections.emptyList())
                    .forEach(subject::addObject);
        }

        // then
        assertTrue(Thread.currentThread().isInterrupted());
        assertThat(
                logCaptor.errorLogs(),
                contains(
                        Matchers.startsWith(
                                "beginNew :: Got IOException when writing startRunningHash to")));
    }

    @Test
    void transactionSidecarWithNoActualSidecarLogsWarning() {
        // given
        given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
        final var firstTransactionInstant =
                LocalDateTime.of(2022, 5, 26, 11, 2, 55).toInstant(ZoneOffset.UTC);
        // set initial running hash
        messageDigest.digest("yumyum".getBytes(StandardCharsets.UTF_8));
        final var startRunningHash = new Hash(messageDigest.digest());
        subject.setRunningHash(startRunningHash);

        // when
        final var faultyRSO =
                new RecordStreamObject(
                        ExpirableTxnRecord.newBuilder().build(),
                        Transaction.getDefaultInstance(),
                        firstTransactionInstant,
                        List.of(TransactionSidecarRecord.newBuilder()));
        subject.addObject(faultyRSO);

        // then
        assertThat(
                logCaptor.warnLogs(),
                contains(
                        Matchers.equalTo(
                                "A sidecar record without an actual sidecar has been received")));
    }

    @Test
    void sidecarFileNameIsCorrectWithIdOf1Digit() {
        // given
        given(streamType.getSidecarExtension())
                .willReturn(RecordStreamType.SIDECAR_RECORD_EXTENSION);
        given(globalDynamicProperties.shouldCompressRecordFilesOnCreation()).willReturn(true);
        final var instant = Instant.parse("2022-05-26T11:02:55.000000000Z");

        // when
        final var actualSidecarFileName = subject.generateSidecarFilePath(instant, 5);

        // then
        final var expected =
                expectedExportDir()
                        + File.separator
                        + "2022-05-26T11_02_55.000000000Z_05."
                        + streamType.getSidecarExtension()
                        + compressedExtension;
        assertEquals(expected, actualSidecarFileName);
    }

    @Test
    void sidecarFileNameIsCorrectWithIdOf2Digits() {
        // given
        given(streamType.getSidecarExtension())
                .willReturn(RecordStreamType.SIDECAR_RECORD_EXTENSION);
        given(globalDynamicProperties.shouldCompressRecordFilesOnCreation()).willReturn(true);
        final var instant = Instant.parse("2022-05-26T11:02:55.000000000Z");

        // when
        final var actualSidecarFileName = subject.generateSidecarFilePath(instant, 10);

        // then
        final var expected =
                expectedExportDir()
                        + File.separator
                        + "2022-05-26T11_02_55.000000000Z_10."
                        + streamType.getSidecarExtension()
                        + compressedExtension;
        assertEquals(expected, actualSidecarFileName);
    }

    @BeforeAll
    static void beforeAll() {
        final var file = new File(expectedExportDir());
        if (!file.exists()) {
            assertTrue(file.mkdir());
        }
    }

    @AfterAll
    static void afterAll() throws IOException {
        TestFileUtils.blowAwayDirIfPresent(expectedExportDir());
    }

    private static String expectedExportDir() {
        return dynamicProperties.pathToBalancesExportDir()
                + File.separator
                + "recordStreamWriterTest";
    }

    private List<TransactionSidecarRecord.Builder> transformToExpectedSidecars(
            final List<TransactionSidecarRecord.Builder> sidecars, final int timesReceived) {
        return Collections.nCopies(timesReceived, sidecars).stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private static final String compressedExtension = ".gz";
    private static final long logPeriodMs = 2000L;
    private static final int maxSidecarFileSize = MB_TO_BYTES;
    private static final int RECORD_STREAM_VERSION = 6;
    private static final int[] FILE_HEADER_VALUES = {
        RECORD_STREAM_VERSION,
        0, // HAPI proto major version
        27, // HAPI proto minor version
        0 // HAPI proto patch version
    };
    private static final byte[] SIG_FILE_HEADER_VALUES = {
        RECORD_STREAM_VERSION,
    };
    public static final EnumSet<SidecarType> allSidecarTypesEnum =
            EnumSet.of(
                    SidecarType.CONTRACT_STATE_CHANGE,
                    SidecarType.CONTRACT_BYTECODE,
                    SidecarType.CONTRACT_ACTION);
    public static final EnumSet<SidecarType> someSidecarTypesEnum =
            EnumSet.of(SidecarType.CONTRACT_BYTECODE, SidecarType.CONTRACT_ACTION);
    private static final TransactionSidecarRecord.Builder bytecodeSidecar =
            TransactionSidecarRecord.newBuilder()
                    .setBytecode(
                            ContractBytecode.newBuilder()
                                    .setRuntimeBytecode(ByteString.copyFrom("runtime".getBytes()))
                                    .build());
    private static final TransactionSidecarRecord.Builder contractActionsSidecar =
            TransactionSidecarRecord.newBuilder()
                    .setActions(
                            ContractActions.newBuilder()
                                    .addContractActions(
                                            ContractAction.newBuilder()
                                                    .setInput(
                                                            ByteString.copyFrom("input".getBytes()))
                                                    .build()));
    private static final TransactionSidecarRecord.Builder stateChangesSidecar =
            TransactionSidecarRecord.newBuilder()
                    .setStateChanges(
                            ContractStateChanges.newBuilder()
                                    .addContractStateChanges(
                                            ContractStateChange.newBuilder()
                                                    .addStorageChanges(
                                                            StorageChange.newBuilder()
                                                                    .setSlot(
                                                                            ByteString.copyFrom(
                                                                                    "slot"
                                                                                            .getBytes()))
                                                                    .setValueRead(
                                                                            ByteString.copyFrom(
                                                                                    "value"
                                                                                            .getBytes()))
                                                                    .build()))
                                    .build());
    private static final List<TransactionSidecarRecord.Builder> allSidecarTypes =
            List.of(stateChangesSidecar, bytecodeSidecar, contractActionsSidecar);
    private static final List<TransactionSidecarRecord.Builder> someSidecarTypes =
            List.of(bytecodeSidecar, contractActionsSidecar);

    @Mock private RecordStreamType streamType;
    @Mock private Signer signer;
    @Mock private GlobalDynamicProperties globalDynamicProperties;
    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private RecordStreamFileWriter subject;

    private MessageDigest messageDigest;
    private static final MockGlobalDynamicProps dynamicProperties = new MockGlobalDynamicProps();
}
