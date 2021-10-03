package com.hedera.services.state.merkle;

import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.common.MutabilityException;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerkleSpecialFilesTest {
	private static final byte[] stuff = "01234578901234578901234578901234578901234567".getBytes(StandardCharsets.UTF_8);
	private static final FileID fid = IdUtils.asFile("0.0.150");
	private static final FileID secondFid = IdUtils.asFile("0.0.151");
	private static final byte[] stuffHash = CommonUtils.noThrowSha384HashOf(stuff);

	private MerkleSpecialFiles subject;

	@BeforeEach
	void setUp() {
		subject = new MerkleSpecialFiles();
	}

	@Test
	void merkleMethodsWork() {
		assertEquals(0x1608d4b49c28983aL, subject.getClassId(), "Unexpected class id");
		assertEquals(1, subject.getVersion(), "Unexpected version");
	}

	@Test
	void emptySpecialFilesNeverMatchHashes() {
		assertFalse(subject.hashMatches(fid, stuffHash), "Empty special files shouldn't match hashes");
	}

	@Test
	void copyCreatesNewMaps() {
		final var copySub = subject.copy();

		assertNotSame(subject.getFileContents(), copySub.getFileContents(),
				"copy() should create new file contents map");
		assertNotSame(subject.getHashCache(), copySub.getHashCache(),
				"copy() should create new hash cache map");
	}

	@Test
	void becomesImmutableOnCopy() {
		final var stuff = "asdf".getBytes(StandardCharsets.UTF_8);

		subject.copy();

		assertThrows(MutabilityException.class, () -> subject.update(fid, stuff),
				"Copies shouldn't be updatable");
		assertThrows(MutabilityException.class, () -> subject.append(fid, stuff),
				"Copies shouldn't be appendable");
	}

	@Test
	void updateAccomplishesTheExpected() {
		subject.update(fid, stuff);

		assertTrue(subject.contains(fid), "Updating should create file if not present");

		assertArrayEquals(stuff, subject.get(fid), "Updated stuff should be identical");

		assertTrue(subject.hashMatches(fid, stuffHash), "Updated stuff should have SHA-384 hash");
	}

	@Test
	void creatingAppendAccomplishesTheExpected() {
		subject.append(secondFid, stuff);

		assertTrue(subject.contains(secondFid), "Appending should create file if not present");

		assertArrayEquals(stuff, subject.get(secondFid), "Appended stuff should be identical");

		assertTrue(subject.hashMatches(secondFid, stuffHash), "Appended stuff should have SHA-384 hash");
	}

	@Test
	void multiAppendAccomplishesTheExpected() {
		subject.append(secondFid, Arrays.copyOfRange(stuff, 0, stuff.length / 2));
		subject.append(secondFid, Arrays.copyOfRange(stuff, stuff.length / 2, stuff.length));

		assertTrue(subject.contains(secondFid), "Appending should create file if not present");

		assertArrayEquals(stuff, subject.get(secondFid), "Appended stuff should be identical");

		assertTrue(subject.hashMatches(secondFid, stuffHash), "Appended stuff should have SHA-384 hash");
	}

	@Test
	void liveFireSerdeWorksWithNonEmpty() throws IOException, ConstructableRegistryException {
		final var baos = new ByteArrayOutputStream();
		final var dos = new SerializableDataOutputStream(baos);
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleSpecialFiles.class, MerkleSpecialFiles::new));

		subject.update(fid, Arrays.copyOfRange(stuff, 0, stuff.length / 2));
		subject.update(secondFid, Arrays.copyOfRange(stuff, stuff.length / 2, stuff.length));

		subject.serialize(dos);
		dos.flush();
		final var bytes = baos.toByteArray();
		final var bais = new ByteArrayInputStream(bytes);
		final var din = new SerializableDataInputStream(bais);

		final var newSubject = new MerkleSpecialFiles();
		newSubject.deserialize(din, MerkleSpecialFiles.CURRENT_VERSION);

		assertArrayEquals(subject.get(fid), newSubject.get(fid),
				"Deserialized contents should match for first file");
		assertArrayEquals(subject.get(secondFid), newSubject.get(secondFid),
				"Deserialized contents should match for second file");
	}

	@Test
	void liveFireSerdeWorksWithEmpty() throws IOException, ConstructableRegistryException {
		final var baos = new ByteArrayOutputStream();
		final var dos = new SerializableDataOutputStream(baos);
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleSpecialFiles.class, MerkleSpecialFiles::new));

		subject.serialize(dos);
		dos.flush();
		final var bytes = baos.toByteArray();
		final var bais = new ByteArrayInputStream(bytes);
		final var din = new SerializableDataInputStream(bais);

		final var newSubject = new MerkleSpecialFiles();
		newSubject.deserialize(din, MerkleSpecialFiles.CURRENT_VERSION);

		assertTrue(newSubject.getFileContents().isEmpty(), "Deserialized instance should be empty");
	}
}