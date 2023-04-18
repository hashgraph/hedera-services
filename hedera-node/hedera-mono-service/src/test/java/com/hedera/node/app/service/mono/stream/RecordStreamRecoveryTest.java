package com.hedera.node.app.service.mono.stream;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.stats.MiscRunningAvgs;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.hedera.node.app.service.mono.stream.Release038xStreamType.RELEASE_038x_STREAM_TYPE;
import static com.hedera.node.app.service.mono.utils.forensics.RecordParsers.parseV6RecordStreamEntriesIn;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class RecordStreamRecoveryTest {
    private static final long BLOCK_PERIOD_MS = 2000L;
    private static final String MEMO = "0.0.3";
    private static final String RECOVERY_ASSETS_LOC = "src/test/resources/recovery";
    static final String ON_DISK_FILES_LOC = RECOVERY_ASSETS_LOC + File.separator + "onDiskFiles";
    static final String RECOVERY_STREAM_ONLY_RSOS_ASSET = "recovery-stream-only.txt";
    static final String ALL_EXPECTED_RSOS_ASSET = "full-stream.txt";
    private static final Hash INITIAL_HASH = new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength()));

    private static final ObjectMapper om = new ObjectMapper();
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

    private RecordStreamManager subject;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException, IOException {
        given(platform.getSelfId()).willReturn(new NodeId(false, 0L));
        final var mockSig = new Signature(SignatureType.RSA, new byte[0]);
        given(platform.sign(any())).willReturn(mockSig);

        given(nodeLocalProperties.isRecordStreamEnabled()).willReturn(true);
        given(nodeLocalProperties.recordLogDir()).willReturn(tmpDir.toString());

        final var recoveryWriter = new RecoveryRecordsWriter(2_000L, ON_DISK_FILES_LOC);
        subject = new RecordStreamManager(
                platform,
                runningAvgs,
                nodeLocalProperties,
                MEMO,
                INITIAL_HASH,
                RELEASE_038x_STREAM_TYPE,
                globalDynamicProperties,
                recoveryWriter);
    }

    @Test
    void includesRecordFilePrefixesSkippedByRecoveryStream() throws IOException, InterruptedException, ExecutionException {
        // given:
        final var allExpectedRsos = loadRecoveryRsosFrom(ALL_EXPECTED_RSOS_ASSET);

        // when:
        replayRecoveryRsosAndFreeze();

        // then:
        assertEntriesMatch(allExpectedRsos, tmpDir.getAbsolutePath());
    }

    private void replayRecoveryRsosAndFreeze() throws InvalidProtocolBufferException, InterruptedException, ExecutionException {
        final var recoveryRsos = loadRecoveryRsosFrom(RECOVERY_STREAM_ONLY_RSOS_ASSET);
        Instant firstConsTimeInBlock = null;

        RecordStreamObject lastAdded = null;
        for (final var recoveryRso : recoveryRsos) {
            final var rso = rsoFrom(recoveryRso);
            if (firstConsTimeInBlock == null) {
                firstConsTimeInBlock = rso.getTimestamp();
            } else if (!inSamePeriod(firstConsTimeInBlock, rso.getTimestamp())) {
                rso.setWriteNewFile();
                firstConsTimeInBlock = rso.getTimestamp();
            }
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
        System.out.println("Loaded " + actualEntries.size() + " entries from " + filesLoc);
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

    static List<RecoveryRSO> loadRecoveryRsosFrom(final String assetName) {
        try {
            try (final var lines = Files.lines(Paths.get(RECOVERY_ASSETS_LOC, assetName))) {
                return lines.map(line -> readJsonValueUnchecked(line, RecoveryRSO.class)).toList();
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

}
