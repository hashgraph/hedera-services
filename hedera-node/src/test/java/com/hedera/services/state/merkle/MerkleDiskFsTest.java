package com.hedera.services.state.merkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import static com.hedera.services.legacy.proto.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.services.utils.EntityIdUtils.asLiteralString;
import static com.hedera.test.utils.IdUtils.asFile;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class MerkleDiskFsTest {
	MerkleDiskFs subject;
	AccountID nodeAccount = AccountID.newBuilder().setAccountNum(3).build();
	FileID file150 = asFile("0.0.150");
	byte[] origContents = "Where, like a pillow on a bed /".getBytes();
	byte[] origFileHash = null;
	byte[] newContents = "A pregnant bank swelled up to rest /".getBytes();
	byte[] newFileHash = null;

	String MOCK_DISKFS_DIR = "src/test/resources/diskFs";

	MerkleDiskFs.ThrowingBytesGetter getter;
	MerkleDiskFs.ThrowingBytesWriter writer;

	@BeforeEach
	private void setup() throws Exception {
		origFileHash = MessageDigest.getInstance("SHA-384").digest(origContents);
		newFileHash = MessageDigest.getInstance("SHA-384").digest(newContents);

		Map<FileID, byte[]> hashes = new HashMap<>();
		hashes.put(IdUtils.asFile("0.0.150"), origFileHash);
		subject = new MerkleDiskFs(
				hashes,
				MOCK_DISKFS_DIR,
				asLiteralString(nodeAccount));

		getter = mock(MerkleDiskFs.ThrowingBytesGetter.class);
		MerkleDiskFs.bytesHelper = getter;
		writer = mock(MerkleDiskFs.ThrowingBytesWriter.class);
		MerkleDiskFs.writeHelper = writer;

		given(getter.allBytesFrom(subject.pathToContentsOf(file150))).willReturn(origContents);
	}

	@AfterEach
	private void cleanup() {
		MerkleDiskFs.writeHelper = (p, c) -> FileUtils.writeByteArrayToFile(p.toFile(), c);
		MerkleDiskFs.bytesHelper = p -> FileUtils.readFileToByteArray(p.toFile());
	}

	@Test
	public void helpersSanityCheck() throws IOException {
		// setup:
		cleanup();

		// given:
		String tmpBase = MOCK_DISKFS_DIR + File.separator + "a" + File.separator + "b" + File.separator;
		Path tmpLoc = Paths.get(tmpBase + "c.txt");
		byte[] tmpMsg = "Testing-1-2-3".getBytes();

		// when:
		MerkleDiskFs.writeHelper.allBytesTo(tmpLoc, tmpMsg);

		// then:
		assertArrayEquals(tmpMsg, MerkleDiskFs.bytesHelper.allBytesFrom(tmpLoc));

		// cleanup:
		tmpLoc.toFile().delete();
		while (!tmpBase.equals(MOCK_DISKFS_DIR + File.separator)) {
			new File(tmpBase).delete();
			tmpBase = tmpBase.substring(0, tmpBase.substring(0, tmpBase.length() - 1).lastIndexOf(File.separator) + 1);
		}
	}

	@Test
	public void toStringWorks() {
		// expect:
		assertEquals(
				"MerkleDiskFs{baseDir=" + MOCK_DISKFS_DIR
						+ ", nodeScopedDir=" + asLiteralString(nodeAccount)
						+ ", fileHashes=[0.0.150 :: " + CommonUtils.hex(origFileHash) + "]"
						+ "}",
				subject.toString()
		);
	}

	@Test
	public void saveFileHashCorrect() throws Exception {
		// setup:
		subject.put(file150, origContents);
		assertArrayEquals(origFileHash, subject.diskContentHash(file150));
		assertArrayEquals(origContents, subject.contentsOf(file150));

		MerkleDiskFs.log = mock(Logger.class);
		subject.checkHashesAgainstDiskContents();

		verify(MerkleDiskFs.log, never()).error(any(String.class));
		// and:
		verify(writer).allBytesTo(subject.pathToContentsOf(file150), origContents);
	}

	@Test
	public void putChangesHash() throws IOException {
		// when:
		subject.put(file150, newContents);

		// then:
		assertArrayEquals(hashWithFileHash(newFileHash), subject.getHash().getValue());
		// and:
		verify(writer).allBytesTo(subject.pathToContentsOf(file150), newContents);
	}

	@Test
	public void fileNotExistNoDebug() throws IOException {
		// setup:
		subject = new MerkleDiskFs("this/doesnt/exist", asLiteralString(nodeAccount));

		given(getter.allBytesFrom(any())).willThrow(IOException.class);

		Assertions.assertSame(MerkleDiskFs.MISSING_CONTENT, subject.contentsOf(file150));
	}

	@Test
	public void fileNotExistDebugEnabled() throws IOException {
		// setup:
		Logger log = mock(Logger.class);
		given(log.isDebugEnabled()).willReturn(true);
		MerkleDiskFs.log = log;
		subject = new MerkleDiskFs("this/doesnt/exist", asLiteralString(nodeAccount));

		given(getter.allBytesFrom(any())).willThrow(IOException.class);

		Assertions.assertSame(MerkleDiskFs.MISSING_CONTENT, subject.contentsOf(file150));
	}

	@Test
	void serializeAbbreviatedWorks() throws IOException {
		var out = mock(SerializableDataOutputStream.class);

		// when:
		subject.serializeAbbreviated(out);

		// then:
		verify(out).writeInt(1);
		verify(out, times(2)).writeLong(0);
		verify(out).writeLong(150);
		verify(out).writeByteArray(origFileHash);
	}

	@Test
	public void serializeWorks() throws IOException {
		// setup:
		byte[] expectedBytes = "ABCDEFGH".getBytes();
		MerkleDiskFs.ThrowingBytesGetter getter = mock(MerkleDiskFs.ThrowingBytesGetter.class);
		MerkleDiskFs.bytesHelper = getter;

		given(getter.allBytesFrom(subject.pathToContentsOf(file150))).willReturn(expectedBytes);
		// and:
		var out = mock(SerializableDataOutputStream.class);

		// when:
		subject.serialize(out);

		// then:
		verify(out).writeInt(1);
		verify(out, times(2)).writeLong(0);
		verify(out).writeLong(150);
		verify(out).writeByteArray(expectedBytes);
	}

	@Test
	public void serializePropagatesException() throws IOException {
		// setup:
		MerkleDiskFs.ThrowingBytesGetter getter = mock(MerkleDiskFs.ThrowingBytesGetter.class);
		MerkleDiskFs.bytesHelper = getter;
		// and:
		var out = mock(SerializableDataOutputStream.class);

		given(getter.allBytesFrom(subject.pathToContentsOf(file150))).willThrow(IOException.class);
		// expect:
		assertThrows(UncheckedIOException.class, () -> subject.serialize(out));
	}

	@Test
	public void copyWorks() {
		// given:
		var copySubject = subject.copy();

		// expect:
		assertNotSame(copySubject, subject);
		assertEquals(subject, copySubject);
	}

	@Test
	public void deserializeAbbreviatedWorks() throws IOException {
		// setup:
		SerializableDataInputStream fin = mock(SerializableDataInputStream.class);
		// and:
		var expectedHash = new Hash(hashWithOrigContents());

		given(fin.readInt()).willReturn(1);
		given(fin.readLong())
				.willReturn(0L)
				.willReturn(0L)
				.willReturn(150L);
		given(fin.readByteArray(48)).willReturn(origFileHash);
		// and:
		var read = new MerkleDiskFs(MOCK_DISKFS_DIR, asLiteralString(nodeAccount));

		// when:
		read.deserializeAbbreviated(fin, expectedHash, MerkleDiskFs.MERKLE_VERSION);

		// then:
		assertEquals(subject, read);
		// and:
		assertEquals(expectedHash, read.getHash());
	}

	@Test
	public void deserializeWorks() throws IOException {
		// setup:
		SerializableDataInputStream fin = mock(SerializableDataInputStream.class);
		// and:
		var expectedHash = new Hash(hashWithOrigContents());

		given(fin.readInt()).willReturn(1);
		given(fin.readLong())
				.willReturn(0L)
				.willReturn(0L)
				.willReturn(150L);
		given(fin.readByteArray(MerkleDiskFs.MAX_FILE_BYTES)).willReturn(origContents);
		// and:
		var read = new MerkleDiskFs(MOCK_DISKFS_DIR, asLiteralString(nodeAccount));

		// when:
		read.deserialize(fin, MerkleDiskFs.MERKLE_VERSION);

		// then:
		assertEquals(subject, read);
		// and:
		assertEquals(expectedHash, read.getHash());
		// and:
		verify(writer).allBytesTo(subject.pathToContentsOf(file150), origContents);
	}

	@Test
	public void hasExpectedHash() {
		// expect:
		assertArrayEquals(hashWithOrigContents(), subject.getHash().getValue());
	}

	@Test
	public void emptyContentsHaveExpectedHash() {
		// expect:
		assertEquals(new Hash(noThrowSha384HashOf(new byte[0])), new MerkleDiskFs().getHash());
	}

	private byte[] hashWithOrigContents() {
		return hashWithFileHash(origFileHash);
	}

	private byte[] hashWithFileHash(byte[] fileHash) {
		byte[] stuff = new byte[3 * 8 + 48 + 4];
		System.arraycopy(Longs.toByteArray(0), 0, stuff, 0, 8);
		System.arraycopy(Longs.toByteArray(0), 0, stuff, 8, 8);
		System.arraycopy(Longs.toByteArray(150), 0, stuff, 16, 8);
		System.arraycopy(Ints.toByteArray(48), 0, stuff,24, 4);
		System.arraycopy(fileHash, 0, stuff, 28, 48);
		return noThrowSha384HashOf(stuff);
	}
}
