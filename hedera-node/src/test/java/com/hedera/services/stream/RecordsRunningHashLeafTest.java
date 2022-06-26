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

import com.hedera.test.utils.TxnUtils;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import io.netty.util.internal.StringUtil;
import com.swirlds.common.threading.futures.StandardFuture;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class RecordsRunningHashLeafTest {
	private static final Hash hash = new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength()));
	private static final RunningHash runningHash = new RunningHash(hash);
	private static final RecordsRunningHashLeaf runningHashLeaf = new RecordsRunningHashLeaf(runningHash);

	@BeforeAll
	public static void setUp() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructables("com.swirlds.common");
	}

	@Test
	void initTest() {
		assertEquals(runningHash, runningHashLeaf.getRunningHash());
		assertTrue(runningHashLeaf.isImmutable());
	}

	@Test
	void copyTest() {
		final var copy = runningHashLeaf.copy();

		assertEquals(runningHashLeaf, copy);
		assertTrue(runningHashLeaf.isImmutable());
		assertFalse(copy.isImmutable());
		// Hashes of the original and the copy should be the same
		CryptoFactory.getInstance().digestSync(copy, DigestType.SHA_384);
		CryptoFactory.getInstance().digestSync(runningHashLeaf, DigestType.SHA_384);
		assertEquals(runningHashLeaf.getHash(), copy.getHash());
	}

	@Test
	void equalsTest() {
		final var sameButDifferent = runningHashLeaf;
		assertEquals(runningHashLeaf, sameButDifferent);
		assertNotEquals(runningHashLeaf, new Object());

		// n-3 running hashes set
		final var hash1 = new Hash(TxnUtils.randomUtf8Bytes(48));
		final var hash2 = new Hash(TxnUtils.randomUtf8Bytes(48));
		final var hash3 = new Hash(TxnUtils.randomUtf8Bytes(48));
		runningHashLeaf.setHash(hash1);
		runningHashLeaf.setHash(hash2);
		runningHashLeaf.setHash(hash3);

		final var diffWithoutNullValues = new RunningHash();
		diffWithoutNullValues.setHash(runningHash.getHash());
		diffWithoutNullValues.setHash(hash1);
		diffWithoutNullValues.setHash(hash2);
		diffWithoutNullValues.setHash(hash3);
		assertNotEquals(runningHashLeaf, diffWithoutNullValues);
	}

	@Test
	void toStringTest() {
		var example = String.format("RecordsRunningHashLeaf's Hash: %s, Hash contained in the leaf: %s, " +
						"nMinus1RunningHash: null, nMinus2RunningHash: null, nMinus3RunningHash: null",
				runningHashLeaf.getHash(),
				runningHash.getHash());
		assertEquals(example, runningHashLeaf.toString());
	}

	@Test
	void hashCodeTest() {
		assertEquals(Objects.hash(runningHash, runningHashLeaf.getNMinus1RunningHash(),
						runningHashLeaf.getNMinus2RunningHash(), runningHashLeaf.getNMinus3RunningHash()),
				runningHashLeaf.hashCode());
	}

	@Test
	void setRunningHashTest() throws InterruptedException {
		// initializes a leaf without setting RunningHash
		final var leafForTestingRunningHash = new RecordsRunningHashLeaf();
		assertNull(leafForTestingRunningHash.getRunningHash());

		// initializes an empty RunningHash
		final var runningHash = new RunningHash();
		// sets it for the leaf
		leafForTestingRunningHash.setRunningHash(runningHash);
		assertEquals(runningHash, leafForTestingRunningHash.getRunningHash());
		assertNull(leafForTestingRunningHash.getRunningHash().getHash());

		// sets Hash for the RunningHash
		final var hash = mock(Hash.class);
		runningHash.setHash(hash);
		assertEquals(runningHash, leafForTestingRunningHash.getRunningHash());
		assertEquals(hash, leafForTestingRunningHash.getRunningHash().getHash());
		assertEquals(hash, leafForTestingRunningHash.currentRunningHash());
	}

	@Test
	void propagatesFatalExecutionException() throws ExecutionException, InterruptedException {
		final var delegate = mock(RunningHash.class);
		final var future = mock(StandardFuture.class);

		given(delegate.getFutureHash()).willReturn(future);
		given(future.get()).willThrow(ExecutionException.class);
		final var subject = new RecordsRunningHashLeaf(delegate);

		assertThrows(IllegalStateException.class, subject::currentRunningHash);
	}

	@Test
	void updatesLastThreeRunningHashes() {
		final var runningHash1 = new RunningHash();
		// sets Hash for the RunningHash
		runningHash1.setHash(new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength())));

		// initializes a leaf with a RunningHash
		final var leafForTestingRunningHash = new RecordsRunningHashLeaf(runningHash1);
		CryptoFactory.getInstance().digestSync(leafForTestingRunningHash, DigestType.SHA_384);
		assertEquals(null, leafForTestingRunningHash.getNMinus3RunningHash().getHash());
		assertEquals(null, leafForTestingRunningHash.getNMinus2RunningHash().getHash());
		assertEquals(null, leafForTestingRunningHash.getNMinus1RunningHash().getHash());
		assertEquals(runningHash1.getHash(), leafForTestingRunningHash.getRunningHash().getHash());

		// update runningHash object
		final var runningHash2 = new RunningHash();
		runningHash2.setHash(new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength())));

		leafForTestingRunningHash.setRunningHash(runningHash2);
		CryptoFactory.getInstance().digestSync(leafForTestingRunningHash, DigestType.SHA_384);
		assertEquals(null, leafForTestingRunningHash.getNMinus3RunningHash().getHash());
		assertEquals(null, leafForTestingRunningHash.getNMinus2RunningHash().getHash());
		assertEquals(runningHash1.getHash(), leafForTestingRunningHash.getNMinus1RunningHash().getHash());
		assertEquals(runningHash2.getHash(), leafForTestingRunningHash.getRunningHash().getHash());

		// update runningHash object
		final var runningHash3 = new RunningHash();
		runningHash3.setHash(new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength())));

		leafForTestingRunningHash.setRunningHash(runningHash3);
		CryptoFactory.getInstance().digestSync(leafForTestingRunningHash, DigestType.SHA_384);
		assertEquals(null, leafForTestingRunningHash.getNMinus3RunningHash().getHash());
		assertEquals(runningHash1.getHash(), leafForTestingRunningHash.getNMinus2RunningHash().getHash());
		assertEquals(runningHash2.getHash(), leafForTestingRunningHash.getNMinus1RunningHash().getHash());
		assertEquals(runningHash3.getHash(), leafForTestingRunningHash.getRunningHash().getHash());

		// update runningHash object
		final var runningHash4 = new RunningHash();
		runningHash4.setHash(new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength())));

		leafForTestingRunningHash.setRunningHash(runningHash4);
		CryptoFactory.getInstance().digestSync(leafForTestingRunningHash, DigestType.SHA_384);
		assertEquals(runningHash1.getHash(), leafForTestingRunningHash.getNMinus3RunningHash().getHash());
		assertEquals(runningHash2.getHash(), leafForTestingRunningHash.getNMinus2RunningHash().getHash());
		assertEquals(runningHash3.getHash(), leafForTestingRunningHash.getNMinus1RunningHash().getHash());
		assertEquals(runningHash4.getHash(), leafForTestingRunningHash.getRunningHash().getHash());

	}

	@Test
	void updateRunningHashInvalidateHashTest() {
		final var runningHash = new RunningHash();
		// sets Hash for the RunningHash
		runningHash.setHash(new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength())));

		// initializes a leaf with a RunningHash
		final var leafForTestingRunningHash = new RecordsRunningHashLeaf(runningHash);
		// digest this leaf
		CryptoFactory.getInstance().digestSync(leafForTestingRunningHash, DigestType.SHA_384);
		assertNotNull(leafForTestingRunningHash.getHash());

		// update runningHash object
		final var newRunningHash = new RunningHash();
		newRunningHash.setHash(new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength())));
		leafForTestingRunningHash.setRunningHash(newRunningHash);
		// the Leaf's Hash should be set to be null after updating the runningHash object
		assertNull(leafForTestingRunningHash.getHash());
	}

	@Test
	void serializationDeserializationTest() throws IOException, InterruptedException, ExecutionException {
		try (final var byteArrayOutputStream = new ByteArrayOutputStream();
			 final var out = new SerializableDataOutputStream(byteArrayOutputStream)) {
			runningHashLeaf.serialize(out);
			byteArrayOutputStream.flush();
			final var bytes = byteArrayOutputStream.toByteArray();
			try (final var byteArrayInputStream = new ByteArrayInputStream(bytes);
				 final var input = new SerializableDataInputStream(byteArrayInputStream)) {
				final var deserialized = new RecordsRunningHashLeaf();
				deserialized.deserialize(input, RecordsRunningHashLeaf.CLASS_VERSION);
				final var originalHash = runningHashLeaf.getRunningHash().getFutureHash().get();
				final var deserializedHash = deserialized.getRunningHash().getFutureHash().get();
				assertEquals(originalHash, deserializedHash);
				assertEquals(hash, originalHash);
			}
		}
	}

	@Test
	void merkleMethodsWork() {
		final var subject = new RecordsRunningHashLeaf();
		assertEquals(RecordsRunningHashLeaf.RELEASE_0280_VERSION, subject.getVersion());
		assertEquals(RecordsRunningHashLeaf.CLASS_ID, subject.getClassId());
	}

	@Test
	void serializationFailsThrowsException() throws ExecutionException, InterruptedException {
		final var runningHash = mock(RunningHash.class);
		final var futureHash = mock(StandardFuture.class);
		final var sout = mock(SerializableDataOutputStream.class);
		final var subject = new RecordsRunningHashLeaf(runningHash);

		given(runningHash.getFutureHash()).willReturn(futureHash);
		given(futureHash.get()).willThrow(InterruptedException.class);
		final var msg = assertThrows(IOException.class, () -> subject.serialize(sout));
		assertTrue(
				msg.getMessage().contains("Got interrupted when getting runningHash when serializing RunningHashLeaf"));
	}
}
