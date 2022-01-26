package com.hedera.services.state.merkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.hedera.services.legacy.proto.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.test.utils.IdUtils.asFile;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(LogCaptureExtension.class)
class MerkleDiskFsTest {
	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private MerkleDiskFs subject;

	private static final FileID file150 = asFile("0.0.150");
	private static final byte[] origContents = "Where, like a pillow on a bed /".getBytes();
	private byte[] origFileHash = null;
	private static final byte[] newContents = "A pregnant bank swelled up to rest /".getBytes();
	private byte[] newFileHash = null;

	private static final String MOCK_DISKFS_DIR = "src/test/resources/diskFs";

	private MerkleDiskFs.ThrowingBytesGetter getter;
	private MerkleDiskFs.ThrowingBytesWriter writer;

	@BeforeEach
	private void setup() throws Exception {
		origFileHash = MessageDigest.getInstance("SHA-384").digest(origContents);
		newFileHash = MessageDigest.getInstance("SHA-384").digest(newContents);

		final Map<FileID, byte[]> hashes = new HashMap<>();
		hashes.put(IdUtils.asFile("0.0.150"), origFileHash);
		subject = new MerkleDiskFs(hashes);

		getter = mock(MerkleDiskFs.ThrowingBytesGetter.class);
		subject.setBytesHelper(getter);
		writer = mock(MerkleDiskFs.ThrowingBytesWriter.class);
		subject.setWriteHelper(writer);

		given(getter.allBytesFrom(subject.pathToContentsOf(file150))).willReturn(origContents);
	}

	@Test
	void helpersSanityCheck() throws IOException {
		String tmpBase = MOCK_DISKFS_DIR + File.separator + "a" + File.separator + "b" + File.separator;
		final var tmpLoc = Paths.get(tmpBase + "c.txt");
		final var tmpMsg = "Testing-1-2-3".getBytes();
		subject = new MerkleDiskFs();

		subject.getWriteHelper().allBytesTo(tmpLoc, tmpMsg);

		assertArrayEquals(tmpMsg, subject.getBytesHelper().allBytesFrom(tmpLoc));

		// cleanup:
		tmpLoc.toFile().delete();
		while (!tmpBase.equals(MOCK_DISKFS_DIR + File.separator)) {
			new File(tmpBase).delete();
			tmpBase = tmpBase.substring(0, tmpBase.substring(0, tmpBase.length() - 1).lastIndexOf(File.separator) + 1);
		}
	}

	@Test
	void getClassIdAndVersion() {
		assertEquals(0xd8a59882c746d0a3L, subject.getClassId());
		assertEquals(1, subject.getVersion());
	}

	@Test
	void nullEqualsWorks() {
		final var sameButDifferent = subject;
		assertNotEquals(null, subject);
		assertEquals(subject, sameButDifferent);
	}

	@Test
	void hashCodeWorks() {
		final Map<FileID, byte[]> hashes = new HashMap<>();
		hashes.put(IdUtils.asFile("0.0.150"), origFileHash);

		assertEquals(Objects.hash(hashes), subject.hashCode());
	}

	@Test
	void containsWorks() {
		assertTrue(subject.contains(IdUtils.asFile("0.0.150")));
	}

	@Test
	void toStringWorks() {
		assertEquals(
				"MerkleDiskFs{fileHashes=[0.0.150 :: " + CommonUtils.hex(origFileHash) + "]}",
				subject.toString());
	}

	@Test
	void checkLogsErrorOnMismatch() throws IOException {
		subject.put(file150, origContents);
		assertArrayEquals(origFileHash, subject.diskContentHash(file150));
		assertArrayEquals(origContents, subject.contentsOf(file150));

		given(getter.allBytesFrom(subject.pathToContentsOf(file150))).willReturn(newContents);

		subject.checkHashesAgainstDiskContents();

		assertThat(
				logCaptor.errorLogs(),
				contains(Matchers.startsWith("State hash doesn't match disk hash for content of '0.0.150'")));
	}

	@Test
	void saveFileHashCorrect() throws IOException {
		subject.put(file150, origContents);
		assertArrayEquals(origFileHash, subject.diskContentHash(file150));
		assertArrayEquals(origContents, subject.contentsOf(file150));

		subject.checkHashesAgainstDiskContents();

		Assertions.assertTrue(logCaptor.errorLogs().isEmpty());
		verify(writer).allBytesTo(subject.pathToContentsOf(file150), origContents);
	}

	@Test
	void putChangesHash() throws IOException {
		subject.put(file150, newContents);

		assertArrayEquals(hashWithFileHash(newFileHash), subject.getHash().getValue());
		verify(writer).allBytesTo(subject.pathToContentsOf(file150), newContents);
	}

	@Test
	void fileNotExistNoDebug() throws IOException {
		subject = new MerkleDiskFs();
		subject.setBytesHelper(getter);
		given(getter.allBytesFrom(any())).willThrow(IOException.class);

		assertThrows(UncheckedIOException.class, () -> subject.contentsOf(file150));
		assertThat(
				logCaptor.errorLogs(),
				contains(Matchers.startsWith("Not able to read '0.0.150' @")));
	}

	@Test
	void serializeAbbreviatedWorks() throws IOException {
		final var out = mock(SerializableDataOutputStream.class);

		subject.serializeExternal(out, null);

		verify(out).writeInt(1);
		verify(out, times(2)).writeLong(0);
		verify(out).writeLong(150);
		verify(out).writeByteArray(origFileHash);
	}

	@Test
	void serializeWorks() throws IOException {
		final var expectedBytes = "ABCDEFGH".getBytes();
		final var getter = mock(MerkleDiskFs.ThrowingBytesGetter.class);
		subject.setBytesHelper(getter);
		given(getter.allBytesFrom(subject.pathToContentsOf(file150))).willReturn(expectedBytes);
		final var out = mock(SerializableDataOutputStream.class);

		subject.serialize(out);

		verify(out).writeInt(1);
		verify(out, times(2)).writeLong(0);
		verify(out).writeLong(150);
		verify(out).writeByteArray(expectedBytes);
	}

	@Test
	void serializePropagatesException() throws IOException {
		final var getter = mock(MerkleDiskFs.ThrowingBytesGetter.class);
		subject.setBytesHelper(getter);
		final var out = mock(SerializableDataOutputStream.class);
		given(getter.allBytesFrom(subject.pathToContentsOf(file150))).willThrow(IOException.class);

		assertThrows(UncheckedIOException.class, () -> subject.serialize(out));
	}

	@Test
	void copyWorks() {
		final var copySubject = subject.copy();

		assertNotSame(subject, copySubject);
		assertEquals(subject, copySubject);
		assertTrue(subject.isImmutable());
	}

	@Test
	void deserializeAbbreviatedWorks() throws IOException {
		final var fin = mock(SerializableDataInputStream.class);
		final var expectedHash = new Hash(hashWithOrigContents());
		given(fin.readInt()).willReturn(1);
		given(fin.readLong())
				.willReturn(0L)
				.willReturn(0L)
				.willReturn(150L);
		given(fin.readByteArray(48)).willReturn(origFileHash);
		final var read = new MerkleDiskFs();

		read.deserializeExternal(fin, null, expectedHash, MerkleDiskFs.MERKLE_VERSION);

		assertEquals(subject, read);
		assertEquals(expectedHash, read.getHash());
	}

	@Test
	void deserializeWorks() throws IOException {
		final var fin = mock(SerializableDataInputStream.class);
		final var expectedHash = new Hash(hashWithOrigContents());
		given(fin.readInt()).willReturn(1);
		given(fin.readLong())
				.willReturn(0L)
				.willReturn(0L)
				.willReturn(150L);
		given(fin.readByteArray(MerkleDiskFs.MAX_FILE_BYTES)).willReturn(origContents);
		final var read = new MerkleDiskFs();
		read.setBytesHelper(getter);
		read.setWriteHelper(writer);

		read.deserialize(fin, MerkleDiskFs.MERKLE_VERSION);

		assertEquals(subject, read);
		assertEquals(expectedHash, read.getHash());
		verify(writer).allBytesTo(subject.pathToContentsOf(file150), origContents);
	}

	@Test
	void hasExpectedHash() {
		assertArrayEquals(hashWithOrigContents(), subject.getHash().getValue());
	}

	@Test
	void emptyContentsHaveExpectedHash() {
		assertEquals(new Hash(noThrowSha384HashOf(new byte[0])), new MerkleDiskFs().getHash());
	}

	private byte[] hashWithOrigContents() {
		return hashWithFileHash(origFileHash);
	}

	private byte[] hashWithFileHash(final byte[] fileHash) {
		final var stuff = new byte[3 * 8 + 48 + 4];
		System.arraycopy(Longs.toByteArray(0), 0, stuff, 0, 8);
		System.arraycopy(Longs.toByteArray(0), 0, stuff, 8, 8);
		System.arraycopy(Longs.toByteArray(150), 0, stuff, 16, 8);
		System.arraycopy(Ints.toByteArray(48), 0, stuff, 24, 4);
		System.arraycopy(fileHash, 0, stuff, 28, 48);
		return noThrowSha384HashOf(stuff);
	}
}
