package com.hedera.services.stream;

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

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class RecordsRunningHashLeafTest {
	private static final Hash hash = new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength()));
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
		// Hashes of the original and the copy should be the same
		CryptoFactory.getInstance().digestSync(copy, DigestType.SHA_384);
		CryptoFactory.getInstance().digestSync(runningHashLeaf, DigestType.SHA_384);
		final Hash copyHash = copy.getHash();
		final Hash leafHash = runningHashLeaf.getHash();
		assertEquals(copyHash, leafHash);
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
	public void updateRunningHashInvalidateHashTest() {
		RunningHash runningHash = new RunningHash();
		// sets Hash for the RunningHash
		runningHash.setHash(new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength())));
		// initializes a leaf with a RunningHash
		RecordsRunningHashLeaf leafForTestingRunningHash = new RecordsRunningHashLeaf(runningHash);
		// digest this leaf
		CryptoFactory.getInstance().digestSync(leafForTestingRunningHash, DigestType.SHA_384);
		Hash leafHash = leafForTestingRunningHash.getHash();
		assertNotNull(leafHash);

		// update runningHash object
		RunningHash newRunningHash = new RunningHash();
		newRunningHash.setHash(new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength())));
		leafForTestingRunningHash.setRunningHash(newRunningHash);
		// the Leaf's Hash should be set to be null after updating the runningHash object
		assertNull(leafForTestingRunningHash.getHash());
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
}
