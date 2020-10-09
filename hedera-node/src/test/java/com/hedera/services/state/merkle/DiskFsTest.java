package com.hedera.services.state.merkle;

import com.hedera.services.files.DiskFs;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.hedera.test.utils.IdUtils.asFile;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(JUnitPlatform.class)
public class DiskFsTest {

	DiskFs subject;
	AccountID nodeAccount = AccountID.newBuilder()
			.setAccountNum(3).build();
	FileID file150 = asFile("0.0.150");
	byte[] origContents = "Where, like a pillow on a bed /".getBytes();
	byte[] fileHash = null;

	@BeforeEach
	private void setup() throws NoSuchAlgorithmException {
		subject = new DiskFs(nodeAccount);
		fileHash = MessageDigest.getInstance("SHA-384").digest(origContents);
	}


	@Test
	public void SaveFileHashCorrect() {
		// setup:
		subject.put(file150, origContents);
		assertArrayEquals(fileHash, subject.getFileHash(file150));
		assertArrayEquals(origContents, subject.getFileContent(file150));

		DiskFs.log = mock(Logger.class);
		subject.checkFileAndDiskHashesMatch();
		verify(DiskFs.log, times(0))
				.error(argThat((String s) -> s.contains("Error: File hash from state does not match")));
	}

	@Test
	public void FileNotExist() {
		// setup:
		subject = new DiskFs();
		DiskFs.log = mock(Logger.class);
		subject.getFileContent(file150);
		verify(DiskFs.log)
				.error(argThat((String s) -> s.contains("Error when reading fileID")));
	}

	@Test
	public void serializeWorks() throws IOException, NoSuchAlgorithmException {

		// setup:
		subject.put(file150, origContents);

		var out = mock(SerializableDataOutputStream.class);

		// when:
		subject.serialize(out);

		// then:
		verify(out).writeInt(1);
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
	public void deserializeWorks() throws IOException, NoSuchAlgorithmException {
		// setup:
		SerializableDataInputStream fin = mock(SerializableDataInputStream.class);

		given(fin.readInt()).willReturn(1);
		given(fin.readLong())
				.willReturn(0L)
				.willReturn(0L)
				.willReturn(150L);
		given(fin.readByteArray(48)).willReturn(fileHash);

		// and:
		var read = new DiskFs(nodeAccount);

		// when:
		read.deserialize(fin, DiskFs.MERKLE_VERSION);

		// then:
		assertEquals(subject, read);
	}

}
