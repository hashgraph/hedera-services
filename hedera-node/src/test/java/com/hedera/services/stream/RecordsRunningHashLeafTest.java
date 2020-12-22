package com.hedera.services.stream;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class RecordsRunningHashLeafTest {
	private static final Hash hash = getRandomHash();
	private static final RunningHash runningHash = new RunningHash(hash);
	private static final RecordsRunningHashLeaf runningHashLeaf = new RecordsRunningHashLeaf(runningHash);

	@BeforeAll
	public static void setUp() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructables("com.swirlds.common");
	}

	@Test
	public void initTest() {
		assertEquals(runningHash, runningHashLeaf.getRunningHash());
		assertFalse(runningHashLeaf.isDataExternal());
		assertTrue(runningHashLeaf.isImmutable());
	}

	@Test
	public void copyTest() {
		RecordsRunningHashLeaf copy = runningHashLeaf.copy();
		assertEquals(runningHashLeaf, copy);
		assertTrue(runningHashLeaf.isImmutable());
		assertFalse(copy.isImmutable());
	}

	@Test
	public void setRunningHashTest() {
		// initializes a leaf without setting RunningHash
		RecordsRunningHashLeaf leafForTestingRunningHash = new RecordsRunningHashLeaf();
		assertNull(leafForTestingRunningHash.getRunningHash());
		// initializes an empty RunningHash
		RunningHash runningHash = new RunningHash();
		// sets it for the leaf
		leafForTestingRunningHash.setRunningHash(runningHash);
		assertEquals(runningHash, leafForTestingRunningHash.getRunningHash());
		assertNull(leafForTestingRunningHash.getRunningHash().getHash());
		// sets Hash for the RunningHash
		Hash hash = mock(Hash.class);
		runningHash.setHash(hash);
		assertEquals(runningHash, leafForTestingRunningHash.getRunningHash());
		assertEquals(hash, leafForTestingRunningHash.getRunningHash().getHash());
	}

	@Test
	public void serializationDeserializationTest() throws IOException, InterruptedException {
		try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		SerializableDataOutputStream out = new SerializableDataOutputStream(byteArrayOutputStream)) {
			runningHashLeaf.serialize(out);
			byteArrayOutputStream.flush();
			byte[] bytes = byteArrayOutputStream.toByteArray();
			try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
				 SerializableDataInputStream input = new SerializableDataInputStream(byteArrayInputStream)) {
				RecordsRunningHashLeaf deserialized = new RecordsRunningHashLeaf();
				deserialized.deserialize(input, RecordsRunningHashLeaf.CLASS_VERSION);
				final Hash originalHash = runningHashLeaf.getRunningHash().getFutureHash().get();
				final Hash deserializedHash = deserialized.getRunningHash().getFutureHash().get();
				assertEquals(originalHash, deserializedHash);
				assertEquals(hash, originalHash);
			}
		}

	}

	private static Hash getRandomHash() {
		byte[] bytes = new byte[DigestType.SHA_384.digestLength()];
		ThreadLocalRandom.current().nextBytes(bytes);
		return new Hash(bytes);
	}
}
