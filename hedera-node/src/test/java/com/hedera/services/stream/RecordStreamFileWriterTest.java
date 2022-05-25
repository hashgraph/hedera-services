package com.hedera.services.stream;

import com.google.protobuf.ByteString;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hederahashgraph.api.proto.java.HashAlgorithm;
import com.hederahashgraph.api.proto.java.HashObject;
import com.hederahashgraph.api.proto.java.RecordStreamFile;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.stream.StreamType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class RecordStreamFileWriterTest {
	RecordStreamFileWriterTest() {}

	@BeforeEach
	void setUp() throws NoSuchAlgorithmException {
		subject = new RecordStreamFileWriter<>(
				expectedExportDir(),
				logPeriodMs,
				signer,
				false,
				streamType
		);

		messageDigest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
		messageDigest.digest("yumyum".getBytes(StandardCharsets.UTF_8));
		startRunningHash = new Hash(messageDigest.digest());
		subject.setRunningHash(startRunningHash);
	}

	@Test
	void testing() {
		// given
		given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
		given(streamType.getExtension()).willReturn(RecordStreamType.RECORD_EXTENSION);
		given(signer.sign(any())).willReturn("signatureBytes".getBytes(StandardCharsets.UTF_8));
		final var firstTransactionInstant = LocalDateTime.of(2022, 5, 24, 11, 2, 55).toInstant(ZoneOffset.UTC);

		// when

		// send all transactions for block 1
		final var firstBlockRSOs =
				generateNRecordStreamObjectsForBlockMStartingFromL(4, 1, firstTransactionInstant);
		firstBlockRSOs.forEach(RSO -> subject.addObject(RSO));

		// send all transactions for block 2
		final var secondBlockRSOs =
				generateNRecordStreamObjectsForBlockMStartingFromL(8, 2,
						firstTransactionInstant.plusSeconds(logPeriodMs / 1000));
		secondBlockRSOs.forEach(subject::addObject);

		// send single transaction for block 3 in order to finish block 2
		generateNRecordStreamObjectsForBlockMStartingFromL(1, 3, firstTransactionInstant.plusSeconds(2 * logPeriodMs / 1000))
				.forEach(subject::addObject);


		// then
		assertProperRecordFile(firstBlockRSOs, 1L, startRunningHash);
		assertProperRecordFile(secondBlockRSOs, 2L,
				firstBlockRSOs.get(firstBlockRSOs.size() - 1).getRunningHash().getHash());
	}

	private List<RecordStreamObject> generateNRecordStreamObjectsForBlockMStartingFromL(
			final int numberOfRSOs,
			final int blockNumber,
			final Instant firstBlockTransactionInstant)
	{
		final var recordStreamObjects = new ArrayList<RecordStreamObject>();
		for (int i = 0; i < numberOfRSOs; i++) {
			final var timestamp =
					Timestamp.newBuilder()
							.setSeconds(firstBlockTransactionInstant.getEpochSecond())
							.setNanos(1000 * i);
			final var transactionRecord =
					TransactionRecord.newBuilder().setConsensusTimestamp(timestamp);
			final var transaction =
					Transaction.newBuilder()
							.setSignedTransactionBytes(ByteString.copyFrom(("block #" + blockNumber + ", transaction #" + i)
									.getBytes(StandardCharsets.UTF_8)));
			final var recordStreamObject =
					new RecordStreamObject(
							transactionRecord.build(),
							transaction.build(),
							Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos())
					);
			final var hashInput = (HASH_PREFIX + (blockNumber + i)).getBytes(StandardCharsets.UTF_8);
			recordStreamObject.getRunningHash().setHash(new Hash(messageDigest.digest(hashInput)));
			recordStreamObject.withBlockNumber(blockNumber);
			recordStreamObjects.add(recordStreamObject);
		}
		return recordStreamObjects;
	}

	private void assertProperRecordFile(
			final List<RecordStreamObject> firstBlockRSOs,
			final long expectedBlock,
			final Hash startRunningHash
	) {
		final var recordStreamFileOptional = importRecordStreamFile(
				subject.generateStreamFilePath(Instant.ofEpochSecond(firstBlockRSOs.get(0).getTimestamp().getEpochSecond())));
		assertTrue(recordStreamFileOptional.isPresent());
		final var recordStreamFile = recordStreamFileOptional.get();

		// assert HAPI semantic version
		assertEquals(recordStreamFile.getHapiProtoVersion(), SemanticVersion.newBuilder()
				.setMajor(FILE_HEADER_VALUES[1])
				.setMinor(FILE_HEADER_VALUES[2])
				.setPatch(FILE_HEADER_VALUES[3]).build()
		);

		// assert startRunningHash
		assertEquals(toProto(startRunningHash), recordStreamFile.getStartObjectRunningHash());

		// assert RSOs
		assertEquals(firstBlockRSOs.size(), recordStreamFile.getRecordFileObjectsCount());
		final var recordFileObjectsList = recordStreamFile.getRecordFileObjectsList();
		for (int i = 0; i < firstBlockRSOs.size(); i++) {
			final var expectedRSO = firstBlockRSOs.get(i);
			final var actualRSOProto = recordFileObjectsList.get(i);
			assertEquals(expectedRSO.getTransaction(), actualRSOProto.getTransaction());
			assertEquals(expectedRSO.getTransactionRecord(), actualRSOProto.getRecord());
		}

		// assert endRunningHash
		final var expectedHashInput = (HASH_PREFIX + (recordStreamFile.getBlockNumber() + (firstBlockRSOs.size() - 1)))
				.getBytes(StandardCharsets.UTF_8);
		assertEquals(toProto(new Hash(messageDigest.digest(expectedHashInput))), recordStreamFile.getEndObjectRunningHash());

		// assert block number
		assertEquals(expectedBlock, recordStreamFile.getBlockNumber());
	}

	private HashObject toProto(final Hash hash) {
		return HashObject.newBuilder()
				.setAlgorithm(HashAlgorithm.SHA_384)
				.setLength(hash.getDigestType().digestLength())
				.setHash(ByteString.copyFrom(hash.getValue()))
				.build();
	}

	private Optional<RecordStreamFile> importRecordStreamFile(final String fileLoc) {
		try (final var fin = new FileInputStream(fileLoc)) {
			// assert record stream version
			final int recordFileVersion = ByteBuffer.wrap(fin.readNBytes(4)).getInt();
			assertEquals(CURRENT_RECORD_STREAM_VERSION, recordFileVersion);

			final var recordStreamFile = RecordStreamFile.parseFrom(fin);
			return Optional.ofNullable(recordStreamFile);
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	@AfterEach
	void tearDown() throws IOException {
		Files.walk(Path.of(expectedExportDir()))
				.map(Path::toFile)
				.forEach(File::delete);
	}

	@BeforeAll
	static void beforeAll() {
		final var file = new File(expectedExportDir());
		if (!file.exists()) {
			assertTrue(file.mkdir());
		}
	}

	@AfterAll
	static void afterAll() {
		final var file = new File(expectedExportDir());
		if (file.exists() && file.isDirectory()) {
			file.delete();
		}
	}

	private static String expectedExportDir() {
		return dynamicProperties.pathToBalancesExportDir() + File.separator + "recordStreamWriterTest";
	}

	private final static long logPeriodMs = 2000L;
	private static final String HASH_PREFIX = "randomPrefix";
	private static final int CURRENT_RECORD_STREAM_VERSION = 6;
	private static final int[] FILE_HEADER_VALUES = {
			6, // Record Stream Version
			0, // HAPI Major version
			27,// HAPI Minor version
			0  // HAPI Patch version
	};

	@Mock
	private StreamType streamType;
	@Mock
	private Signer signer;
	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private RecordStreamFileWriter<RecordStreamObject> subject;

	private Hash startRunningHash;
	private MessageDigest messageDigest;
	private final static MockGlobalDynamicProps dynamicProperties = new MockGlobalDynamicProps();
}