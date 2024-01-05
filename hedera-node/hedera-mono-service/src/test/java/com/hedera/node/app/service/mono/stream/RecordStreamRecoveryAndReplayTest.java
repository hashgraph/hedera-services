/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.orderedRecordFilesFrom;
import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.orderedSidecarFilesFrom;
import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.readMaybeCompressedRecordStreamFile;
import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.readMaybeCompressedSidecarFile;
import static com.hedera.node.app.service.mono.stream.Release038xStreamType.RELEASE_038x_STREAM_TYPE;
import static com.hedera.node.app.service.mono.utils.forensics.RecordParsers.parseV6RecordStreamEntriesIn;
import static com.hedera.node.app.service.mono.utils.forensics.RecordParsers.parseV6SidecarRecordsByConsTimeIn;
import static com.hedera.node.app.service.mono.utils.forensics.RecordParsers.visitWithSidecars;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;
import static java.util.Comparator.comparing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.stats.MiscRunningAvgs;
import com.hedera.node.app.service.mono.utils.forensics.RecordParsers;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.SidecarFile;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * This unit test does "live" testing of a full event stream recovery or PCES replay.
 * The only mocks are the property sources and the {@link Platform#sign(byte[])} method;
 * but the {@link RecordStreamManager} internals are fully exercised, reading and writing
 * from a temporary directory.
 *
 * <p>When testing <b>recovery</b>, first we copy the {@code .rcd.gz} files from a test
 * resource directory into the temporary directory. Then we run the recovery process,
 * intentionally <b>skipping</b> the first 4 record stream items from the first
 * {@code .rcd.gz} file. Because the {@link RecordStreamManager} is started in recovery mode,
 * it must still find and include these 4 items (and their sidecars) from the "golden" record
 * file on disk that includes the consensus time at which recovery began.
 *
 * <p>When testing <b>replay</b>, after copying the {@code .rcd.gz} file from the test
 * resource directory, we remove the last file after computing its expected metadata hash.
 * We then run the replay process, which should re-create the last file, and confirm the
 * metadata hash in the signature file is as expected.
 */
@ExtendWith(MockitoExtension.class)
class RecordStreamRecoveryAndReplayTest {
    // The running hash you get after starting with the running hash of the "golden"
    // record file on disk for this test; and then adding the 4 record stream items NOT
    // replayed during this test's simulated event stream recovery
    private static final String SAVED_STATE_RUNNING_HASH =
            "df6f47019d32c0fa9410b280b40e8235dda744782518d8d9b1b46708d48e8c3ab8cb416c81c585f20b4e23f5951d3890";
    private static final long START_TEST_ASSET_BLOCK_NO = 2;
    private static final long BLOCK_PERIOD_MS = 2000L;
    private static final String MEMO = "0.0.3";
    static final String RECOVERY_ASSETS_LOC = "src/test/resources/recovery";
    static final String ON_DISK_FILES_LOC = RECOVERY_ASSETS_LOC + File.separator + "onDiskFiles";
    static final String ON_DISK_FILES_AND_SIDECARS_LOC =
            RECOVERY_ASSETS_LOC + File.separator + "onDiskFilesAndSidecars";
    static final String RECOVERY_STREAM_ONLY_RSOS_ASSET = "recovery-stream-only.txt";
    static final String ALL_EXPECTED_RSOS_ASSET = "full-stream.txt";

    private static final ObjectMapper om = new ObjectMapper();

    /**
     * A temporary directory that serves as the record stream directory for the test; we first
     * copy the assets from the test resources to this directory, and then run the test.
     */
    @TempDir
    private File tmpDir;

    @Mock
    private Platform platform;

    @Mock
    private MiscRunningAvgs runningAvgs;

    @Mock
    private NodeLocalProperties nodeLocalProperties;

    @Mock
    private GlobalDynamicProperties globalDynamicProperties;

    @Mock
    private Predicate<File> tryDeletion;

    // When running a PCES replay test, the expected name of the last record file
    @Nullable
    private String expectedLastFile;
    // When running a PCES replay test, the expected metadata hash of the last record file
    @Nullable
    private String expectedLastMetadataHash;

    private RecordStreamManager subject;

    @BeforeEach
    void setup() {
        given(platform.getSelfId()).willReturn(new NodeId(0L));

        given(nodeLocalProperties.isRecordStreamEnabled()).willReturn(true);
        given(nodeLocalProperties.recordLogDir()).willReturn(tmpDir.toString());
        given(nodeLocalProperties.sidecarDir()).willReturn("");
        given(globalDynamicProperties.shouldCompressRecordFilesOnCreation()).willReturn(true);
    }

    /**
     * Sets the test subject to a {@link RecordStreamManager} that will receive record stream objects
     * created during {@link InitTrigger#EVENT_STREAM_RECOVERY}. The subject
     * must reconcile the items replayed during recovery with the "golden" record file on disk that
     * contains the consensus time at which recovery begins; and it must otherwise delete and recreate
     * any record files it finds on disk.
     *
     * @param onDiskLocForTest the location of the test assets to use
     */
    private void givenRealSubjectRecoveringFrom(final String onDiskLocForTest)
            throws IOException, NoSuchAlgorithmException {
        final var copiedFiles = cpAssetsToTmpDirFrom(onDiskLocForTest);
        final var lastFile = lastCopiedFile(copiedFiles);
        final var lastFilePath = Paths.get(tmpDirLoc(), lastFile);
        // Delete one file just to get a bit more scenario coverage
        if (!lastFilePath.toFile().delete()) {
            throw new IllegalStateException("Could not delete " + lastFilePath);
        }
        final var recoveryWriter = new RecoveryRecordsWriter(2_000L, tmpDir.getAbsolutePath());
        final var mockSig = new Signature(SignatureType.RSA, new byte[0]);
        given(platform.sign(any())).willReturn(mockSig);
        subject = new RecordStreamManager(
                platform,
                runningAvgs,
                nodeLocalProperties,
                MEMO,
                new Hash(CommonUtils.unhex(SAVED_STATE_RUNNING_HASH)),
                RELEASE_038x_STREAM_TYPE,
                globalDynamicProperties,
                recoveryWriter,
                File::delete);
    }

    /**
     * Sets the test subject to a {@link RecordStreamManager} that will receive record stream objects
     * created while {@link PlatformStatus#REPLAYING_EVENTS}. The subject
     * does not need to do any work to reconcile the items replayed with record files on
     * disk, since it should instead just skip the blocks corresponding to those old files.
     *
     * @param onDiskLocForTest the location of the test assets to use
     */
    private void givenRealSubjectReplayingFrom(final String onDiskLocForTest)
            throws IOException, NoSuchAlgorithmException {
        final var copiedFiles = cpAssetsToTmpDirFrom(onDiskLocForTest);
        expectedLastFile = lastCopiedFile(copiedFiles);
        final var lastFilePath = Paths.get(tmpDirLoc(), expectedLastFile);
        expectedLastMetadataHash = computeHexedMetadataHashOfRecordFile(lastFilePath);
        // Delete one file to ensure we re-create it as expected
        if (!lastFilePath.toFile().delete()) {
            throw new IllegalStateException("Could not delete " + lastFilePath);
        }
        final var mockSig = new Signature(SignatureType.RSA, new byte[0]);
        given(platform.sign(any())).willReturn(mockSig);
        subject = new RecordStreamManager(
                platform,
                runningAvgs,
                nodeLocalProperties,
                MEMO,
                new Hash(CommonUtils.unhex(SAVED_STATE_RUNNING_HASH)),
                RELEASE_038x_STREAM_TYPE,
                globalDynamicProperties,
                null,
                File::delete);
    }

    private void givenDeletionIncapableSubjectRecoveringFrom(final String onDiskLocForTest)
            throws IOException, NoSuchAlgorithmException {
        cpAssetsToTmpDirFrom(onDiskLocForTest);
        final var recoveryWriter = new RecoveryRecordsWriter(2_000L, tmpDir.getAbsolutePath());
        subject = new RecordStreamManager(
                platform,
                runningAvgs,
                nodeLocalProperties,
                MEMO,
                new Hash(CommonUtils.unhex(SAVED_STATE_RUNNING_HASH)),
                RELEASE_038x_STREAM_TYPE,
                globalDynamicProperties,
                recoveryWriter,
                tryDeletion);
    }

    @Test
    void directRsoReplayAlsoReproducesSidecars()
            throws IOException, NoSuchAlgorithmException, ExecutionException, InterruptedException {
        given(globalDynamicProperties.getSidecarMaxSizeMb()).willReturn(256);
        givenRealSubjectRecoveringFrom(ON_DISK_FILES_AND_SIDECARS_LOC);
        // and:
        final var expectedSidecars = allSidecarFilesFrom(ON_DISK_FILES_AND_SIDECARS_LOC);

        // when:
        replayDirectlyFromRsos(ON_DISK_FILES_AND_SIDECARS_LOC, 4);

        // then:
        final var actualSidecars = allSidecarFilesFrom(tmpDir.getAbsolutePath());
        for (final var compressedSidecarFile : expectedSidecars.keySet()) {
            final var expectedSidecar = expectedSidecars.get(compressedSidecarFile);
            final var actualSidecar = actualSidecars.get(compressedSidecarFile);
            assertEquals(expectedSidecar, actualSidecar, "Wrong sidecar for " + compressedSidecarFile);
        }
    }

    @Test
    void includesRecordFilePrefixesSkippedByRecoveryStreamWithIndependentRsoSource()
            throws IOException, InterruptedException, ExecutionException, NoSuchAlgorithmException {
        givenRealSubjectRecoveringFrom(ON_DISK_FILES_LOC);
        // and:
        final var expectedMeta = allRecordFileMetaFrom(ON_DISK_FILES_LOC);
        final var allExpectedRsos = loadRsosFrom(ALL_EXPECTED_RSOS_ASSET);

        // when:
        replayRsosAndFreeze();
        // and:
        final var actualMeta = allRecordFileMetaFrom(tmpDir.getAbsolutePath());

        // then:
        assertEntriesMatch(allExpectedRsos, tmpDir.getAbsolutePath());
        for (final var compressedRecordFile : expectedMeta.keySet()) {
            final var expectedFileMeta = expectedMeta.get(compressedRecordFile);
            final var actualFileMeta = actualMeta.get(compressedRecordFile);
            assertEquals(expectedFileMeta, actualFileMeta, "Wrong meta for " + compressedRecordFile);
        }
    }

    @Test
    void metadataHashReplayedCorrectly()
            throws IOException, NoSuchAlgorithmException, ExecutionException, InterruptedException {
        given(globalDynamicProperties.getSidecarMaxSizeMb()).willReturn(256);
        givenRealSubjectReplayingFrom(ON_DISK_FILES_LOC);

        // when:
        replayRsosAndFreeze();

        // then the newly re-created last signature file has the correct metadata hash
        final var expectedLastSigFileName =
                Objects.requireNonNull(expectedLastFile).substring(0, expectedLastFile.lastIndexOf(".rcd"))
                        + ".rcd_sig";
        final var finalSigLoc = Paths.get(tmpDirLoc(), expectedLastSigFileName).toString();
        final var sigFile =
                RecordStreamingUtils.readSignatureFile(finalSigLoc).getRight().get();
        final var actualMetadataHash = CommonUtils.hex(
                sigFile.getMetadataSignature().getHashObject().getHash().toByteArray());
        assertEquals(expectedLastMetadataHash, actualMetadataHash);
    }

    @Test
    void failsFastIfUnableToDeleteExistingFileDuringRecovery() throws IOException, NoSuchAlgorithmException {
        givenDeletionIncapableSubjectRecoveringFrom(ON_DISK_FILES_LOC);

        final var e = assertThrows(IllegalStateException.class, this::replayRsosAndFreeze);

        assertEquals(
                "Could not delete existing record file '2023-04-18T14_08_20.465612003Z.rcd.gz' "
                        + "to replace during recovery, aborting",
                e.getMessage());
    }

    private void replayDirectlyFromRsos(final String loc, final int rsosToSkip)
            throws IOException, ExecutionException, InterruptedException {
        final var entries = RecordParsers.parseV6RecordStreamEntriesIn(loc);
        final var sidecarRecords = parseV6SidecarRecordsByConsTimeIn(loc);

        final var numSkipped = new AtomicInteger();
        final AtomicReference<Instant> firstConsTimeInBlock = new AtomicReference<>(null);
        final var blockNo = new AtomicLong(1);
        final AtomicReference<RecordStreamObject> lastAdded = new AtomicReference<>(null);
        visitWithSidecars(entries, sidecarRecords, (entry, records) -> {
            if (numSkipped.getAndIncrement() < rsosToSkip) {
                return;
            }
            final var sidecars =
                    records.stream().map(TransactionSidecarRecord::toBuilder).toList();
            final var rso = new RecordStreamObject(
                    entry.txnRecord(), entry.submittedTransaction(), entry.consensusTime(), sidecars);
            if (firstConsTimeInBlock.get() == null) {
                firstConsTimeInBlock.set(rso.getTimestamp());
            } else if (!inSamePeriod(firstConsTimeInBlock.get(), rso.getTimestamp())) {
                blockNo.getAndIncrement();
                rso.setWriteNewFile();
                firstConsTimeInBlock.set(rso.getTimestamp());
            }
            rso.withBlockNumber(blockNo.get());
            subject.addRecordStreamObject(rso);
            lastAdded.set(rso);
        });
        subject.setInFreeze(true);
        if (lastAdded.get() != null) {
            lastAdded.get().getRunningHash().getFutureHash().get();
        }
    }

    private Map<String, RecordStreamFile> allRecordFileMetaFrom(final String loc) throws IOException {
        final var files = orderedRecordFilesFrom(loc, f -> true).stream().toList();
        final Map<String, RecordStreamFile> ans = new HashMap<>();
        for (final var f : files) {
            var recordFile = readMaybeCompressedRecordStreamFile(f).getRight().get();
            // We don't care about the version; and we already compare the items one-by-one (plus their running hashes)
            recordFile = recordFile.toBuilder()
                    .clearHapiProtoVersion()
                    .clearRecordStreamItems()
                    .build();
            final var p = Paths.get(f);
            ans.put(p.getName(p.getNameCount() - 1).toString(), recordFile);
        }
        return ans;
    }

    private Map<String, SidecarFile> allSidecarFilesFrom(final String loc) throws IOException {
        final var files = orderedSidecarFilesFrom(loc).stream().toList();
        final Map<String, SidecarFile> ans = new HashMap<>();
        for (final var f : files) {
            var sidecarFile = readMaybeCompressedSidecarFile(f);
            final var p = Paths.get(f);
            ans.put(p.getName(p.getNameCount() - 1).toString(), sidecarFile);
        }
        return ans;
    }

    private void replayRsosAndFreeze() throws InvalidProtocolBufferException, InterruptedException, ExecutionException {
        final var recoveryRsos = loadRsosFrom(RECOVERY_STREAM_ONLY_RSOS_ASSET);
        Instant firstConsTimeInBlock = null;

        var blockNo = START_TEST_ASSET_BLOCK_NO;
        RecordStreamObject lastAdded = null;
        for (final var recoveryRso : recoveryRsos) {
            final var rso = rsoFrom(recoveryRso);
            if (firstConsTimeInBlock == null) {
                firstConsTimeInBlock = rso.getTimestamp();
            } else if (!inSamePeriod(firstConsTimeInBlock, rso.getTimestamp())) {
                blockNo++;
                rso.setWriteNewFile();
                firstConsTimeInBlock = rso.getTimestamp();
            }
            rso.withBlockNumber(blockNo);
            subject.addRecordStreamObject(rso);
            lastAdded = rso;
        }
        subject.setInFreeze(true);

        if (lastAdded != null) {
            lastAdded.getRunningHash().getFutureHash().get();
        }
    }

    private boolean inSamePeriod(@NonNull final Instant then, @NonNull final Instant now) {
        return getPeriod(now, BLOCK_PERIOD_MS) == getPeriod(then, BLOCK_PERIOD_MS);
    }

    static RecordStreamObject rsoFrom(final RecoveryRSO recovered) throws InvalidProtocolBufferException {
        return new RecordStreamObject(
                TransactionRecord.parseFrom(decoded(recovered.getB64Record())),
                Transaction.parseFrom(decoded(recovered.getB64Transaction())),
                Instant.parse(recovered.getConsensusTime()));
    }

    static byte[] decoded(final String base64) {
        return Base64.getDecoder().decode(base64);
    }

    private void assertEntriesMatch(final List<RecoveryRSO> expected, final String filesLoc) throws IOException {
        final var actualEntries = parseV6RecordStreamEntriesIn(filesLoc);
        assertEquals(expected.size(), actualEntries.size());
        for (int i = 0; i < expected.size(); i++) {
            final var expectedEntry = expected.get(i);
            final var actualEntry = actualEntries.get(i);

            final var expectedTime = Instant.parse(expectedEntry.getConsensusTime());
            assertEquals(expectedTime, actualEntry.consensusTime());
        }
    }

    private static <T> T readJsonValueUnchecked(final String line, final Class<T> type) {
        try {
            return om.readValue(line, type);
        } catch (final JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<RecoveryRSO> loadRsosFrom(final String assetName) {
        try {
            try (final var lines = Files.lines(Paths.get(RECOVERY_ASSETS_LOC, assetName))) {
                return lines.map(line -> readJsonValueUnchecked(line, RecoveryRSO.class))
                        .toList();
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> cpAssetsToTmpDirFrom(@NonNull final String sourceLoc) throws IOException {
        final var nodeScopedDir = new File(tmpDirLoc());
        Files.createDirectories(nodeScopedDir.toPath());
        final List<String> copiedFileNames = new ArrayList<>();
        Files.list(Paths.get(sourceLoc)).forEach(path -> {
            try {
                Files.copy(
                        path,
                        Paths.get(
                                nodeScopedDir.getAbsolutePath(),
                                path.getFileName().toString()));
                copiedFileNames.add(path.getName(path.getNameCount() - 1).toString());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return copiedFileNames;
    }

    private String tmpDirLoc() {
        return RecordStreamManager.effectiveLogDir(tmpDir.getAbsolutePath(), MEMO);
    }

    private String computeHexedMetadataHashOfRecordFile(final Path at) throws IOException, NoSuchAlgorithmException {
        final var recordStreamFile =
                readMaybeCompressedRecordStreamFile(at.toString()).getRight().get();
        final var baos = new ByteArrayOutputStream();
        try (final var out = new SerializableDataOutputStream(baos)) {
            for (final int part : RELEASE_038x_STREAM_TYPE.getFileHeader()) {
                out.writeInt(part);
            }
            out.write(recordStreamFile.getStartObjectRunningHash().getHash().toByteArray());
            out.write(recordStreamFile.getEndObjectRunningHash().getHash().toByteArray());
            out.writeLong(recordStreamFile.getBlockNumber());
        }
        final var digest = MessageDigest.getInstance(Cryptography.DEFAULT_DIGEST_TYPE.algorithmName());
        return CommonUtils.hex(digest.digest(baos.toByteArray()));
    }

    private String lastCopiedFile(final List<String> copiedFilesNames) {
        return copiedFilesNames.stream()
                .filter(RecordStreamingUtils::isRecordFile)
                .sorted(comparing(RecordStreamingUtils::parseRecordFileConsensusTime)
                        .reversed())
                .findFirst()
                .get();
    }
}
