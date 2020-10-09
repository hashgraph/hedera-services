package com.hedera.services.state.merkle;

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import static com.hedera.test.utils.IdUtils.asFile;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(JUnitPlatform.class)
public class MerkleDiskFsTest {
	MerkleDiskFs subject;
	AccountID nodeAccount = AccountID.newBuilder()
			.setAccountNum(3).build();
	FileID file150 = asFile("0.0.150");
	byte[] origContents = "Where, like a pillow on a bed /".getBytes();
	byte[] fileHash = null;

	String MOCK_DISKFS_DIR = "src/test/resources/diskFs";

	@BeforeEach
	private void setup() throws NoSuchAlgorithmException {
		fileHash = MessageDigest.getInstance("SHA-384").digest(origContents);

		Map<FileID, byte[]> hashes = new HashMap<>();
		hashes.put(IdUtils.asFile("0.0.150"), fileHash);
		subject = new MerkleDiskFs(
				hashes,
				MOCK_DISKFS_DIR,
				EntityIdUtils.asLiteralString(nodeAccount));
	}

	@Test
	public void toStringWorks() {
		// expect:
		assertEquals(
				"MerkleDiskFs{baseDir=" + MOCK_DISKFS_DIR
						+ ", nodeScopedDir=" + EntityIdUtils.asLiteralString(nodeAccount)
						+ ", fileHashes=[0.0.150 :: " + CommonUtils.hex(fileHash) + "]"
						+ "}",
				subject.toString()
		);
	}

	@Test
	public void SaveFileHashCorrect() {
		// setup:
		subject.put(file150, origContents);
		assertArrayEquals(fileHash, subject.diskContentHash(file150));
		assertArrayEquals(origContents, subject.contentsOf(file150));

		MerkleDiskFs.log = mock(Logger.class);
		subject.checkHashesAgainstDiskContents();
		verify(MerkleDiskFs.log, never()).error(any(String.class));
	}

	@Test
	public void FileNotExist() {
		// setup:
		subject = new MerkleDiskFs("this/doesnt/exist", EntityIdUtils.asLiteralString(nodeAccount));

		Assertions.assertSame(MerkleDiskFs.MISSING_CONTENT, subject.contentsOf(file150));
	}

	@Test
	public void serializeWorks() throws IOException {
		var out = mock(SerializableDataOutputStream.class);

		// when:
		subject.serialize(out);

		// then:
		verify(out).writeInt(1);
		verify(out, times(2)).writeLong(0);
		verify(out).writeLong(150);
		verify(out).writeByteArray(fileHash);
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
	public void deserializeWorks() throws IOException {
		// setup:
		SerializableDataInputStream fin = mock(SerializableDataInputStream.class);

		given(fin.readInt()).willReturn(1);
		given(fin.readLong())
				.willReturn(0L)
				.willReturn(0L)
				.willReturn(150L);
		given(fin.readByteArray(48)).willReturn(fileHash);
		// and:
		var read = new MerkleDiskFs(MOCK_DISKFS_DIR, EntityIdUtils.asLiteralString(nodeAccount));

		// when:
		read.deserialize(fin, MerkleDiskFs.MERKLE_VERSION);
		read.setFsBaseDir(MOCK_DISKFS_DIR);
		read.setFsNodeScopedDir(EntityIdUtils.asLiteralString(nodeAccount));

		// then:
		assertEquals(subject, read);
	}
}
