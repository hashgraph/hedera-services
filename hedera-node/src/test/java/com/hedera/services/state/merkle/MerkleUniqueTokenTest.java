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

import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;

import static com.hedera.services.state.merkle.internals.BitPackUtils.MAX_NUM_ALLOWED;
import static com.hedera.services.state.merkle.internals.BitPackUtils.packedTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class MerkleUniqueTokenTest {
	private MerkleUniqueToken subject;

	private final long numbers = BitPackUtils.packedNums(123, 456);
	private EntityId owner;
	private EntityId spender;
	private EntityId otherOwner;
	private byte[] metadata;
	private byte[] otherMetadata;
	private RichInstant timestamp;
	private RichInstant otherTimestamp;

	private static long timestampL = 1_234_567L;

	@BeforeEach
	void setup() {
		owner = new EntityId(0, 0, 3);
		otherOwner = new EntityId(0, 0, 4);
		spender = new EntityId(0, 0, 5);
		metadata = "Test NFT".getBytes();
		otherMetadata = "Test NFT2".getBytes();
		timestamp = RichInstant.fromJava(Instant.ofEpochSecond(timestampL));
		otherTimestamp = RichInstant.fromJava(Instant.ofEpochSecond(1_234_568L));

		subject = new MerkleUniqueToken(owner, metadata, timestamp);
		subject.setKey(new EntityNumPair(numbers));
		subject.setSpender(spender);
	}

	@Test
	void equalsContractWorks() {
		// setup:
		final var key = new EntityNumPair(numbers);
		// given
		var other = new MerkleUniqueToken(owner, metadata, otherTimestamp);
		other.setKey(key);
		var other2 = new MerkleUniqueToken(owner, otherMetadata, timestamp);
		other2.setKey(key);
		var other3 = new MerkleUniqueToken(otherOwner, metadata, timestamp);
		other3.setKey(key);
		var other4 = new MerkleUniqueToken(owner, metadata, timestamp);
		other4.setKey(new EntityNumPair(numbers + 1));
		var other5 = new MerkleUniqueToken(owner, metadata, timestamp);
		other5.setKey(key);
		var identical = new MerkleUniqueToken(owner, metadata, timestamp);
		identical.setKey(key);
		identical.setSpender(spender);

		// expect
		assertNotEquals(subject, new Object());
		assertNotEquals(subject, other);
		assertNotEquals(subject, other2);
		assertNotEquals(subject, other3);
		assertNotEquals(subject, other4);
		assertNotEquals(subject, other5);
		assertEquals(subject, identical);
	}

	@Test
	void hashCodeWorks() {
		// given:
		var identical = new MerkleUniqueToken(owner, metadata, timestamp);
		identical.setKey(new EntityNumPair(numbers));
		identical.setSpender(spender);
		var other = new MerkleUniqueToken(otherOwner, otherMetadata, otherTimestamp);

		// expect:
		assertNotEquals(subject.hashCode(), other.hashCode());
		assertEquals(subject.hashCode(), identical.hashCode());
	}

	@Test
	void toStringWorks() {
		// given:
		assertEquals("MerkleUniqueToken{" +
						"id=0.0.123.456, owner=0.0.3, " +
						"creationTime=1970-01-15T06:56:07Z, " +
						"metadata=" + Arrays.toString(metadata) + ", spender=0.0.5}",
				subject.toString());
	}

	@Test
	void copyWorks() {
		// given:
		var copyNft = subject.copy();
		var other = new Object();

		// expect:
		assertNotSame(copyNft, subject);
		assertEquals(subject, copyNft);
		assertNotEquals(subject, other);
	}

	@Test
	void serializeWorks() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		// and:
		InOrder inOrder = inOrder(out);

		// when:
		subject.serialize(out);

		// then:
		inOrder.verify(out).writeInt(owner.identityCode());
		inOrder.verify(out).writeLong(packedTime(timestamp.getSeconds(), timestamp.getNanos()));
		inOrder.verify(out).writeByteArray(metadata);
		inOrder.verify(out).writeLong(numbers);
		inOrder.verify(out).writeInt(spender.identityCode());
	}

	@Test
	void deserializeWorksPre0180() throws IOException {
		// setup:
		SerializableDataInputStream in = mock(SerializableDataInputStream.class);
		// and:
		subject.setSpender(EntityId.MISSING_ENTITY_ID);
		final var packedTime = packedTime(timestamp.getSeconds(), timestamp.getNanos());

		given(in.readByteArray(anyInt())).willReturn(metadata);
		given(in.readLong()).willReturn(packedTime);
		given(in.readInt()).willReturn(owner.identityCode());

		// and:
		var read = new MerkleUniqueToken();

		// when:
		read.deserialize(in, MerkleUniqueToken.PRE_RELEASE_0180_VERSION);

		// then:
		assertNotEquals(subject, read);
		// and when:
		read.setKey(new EntityNumPair(numbers));
		// then:
		assertEquals(subject, read);
	}

	@Test
	void deserializeWorksPost0180() throws IOException {
		// setup:
		SerializableDataInputStream in = mock(SerializableDataInputStream.class);
		// and:
		subject.setSpender(EntityId.MISSING_ENTITY_ID);
		final var packedTime = packedTime(timestamp.getSeconds(), timestamp.getNanos());

		given(in.readByteArray(anyInt())).willReturn(metadata);
		given(in.readLong()).willReturn(packedTime).willReturn(numbers);
		given(in.readInt()).willReturn(owner.identityCode());

		// and:
		var read = new MerkleUniqueToken();

		// when:
		read.deserialize(in, MerkleUniqueToken.RELEASE_0180_VERSION);

		// then:
		assertEquals(subject, read);
	}

	@Test
	void deserializeWorksForV0250() throws IOException {
		// setup:
		SerializableDataInputStream in = mock(SerializableDataInputStream.class);
		// and:
		final var packedTime = packedTime(timestamp.getSeconds(), timestamp.getNanos());

		given(in.readByteArray(anyInt())).willReturn(metadata);
		given(in.readLong()).willReturn(packedTime).willReturn(numbers);
		given(in.readInt())
				.willReturn(owner.identityCode())
				.willReturn(spender.identityCode());

		// and:
		var read = new MerkleUniqueToken();

		// when:
		read.deserialize(in, MerkleUniqueToken.RELEASE_0250_VERSION);

		// then:
		assertEquals(subject, read);
	}

	@Test
	void liveFireSerdeWorks() throws IOException, ConstructableRegistryException {
		// setup:
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final var dos = new SerializableDataOutputStream(baos);
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleUniqueToken.class, MerkleUniqueToken::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(EntityId.class, EntityId::new));

		// given:
		subject.serialize(dos);
		dos.flush();
		// and:
		final var bytes = baos.toByteArray();
		final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		final var din = new SerializableDataInputStream(bais);

		// when:
		final var newSubject = new MerkleUniqueToken();
		newSubject.deserialize(din, MerkleToken.CURRENT_VERSION);

		// then:
		assertEquals(subject, newSubject);
	}

	@Test
	void merkleMethodsWork() {
		// expect;
		assertEquals(MerkleUniqueToken.CURRENT_VERSION, subject.getVersion());
		assertEquals(MerkleUniqueToken.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertTrue(subject.isLeaf());
	}

	@Test
	void setsAndGetsOwner() {
		// setup:
		final var smallNumOwner = new EntityId(0, 0, 1);
		final var largeNumOwner = new EntityId(0, 0, MAX_NUM_ALLOWED);

		// expect:
		subject.setOwner(smallNumOwner);
		assertEquals(smallNumOwner, subject.getOwner());

		// and expect:
		subject.setOwner(largeNumOwner);
		assertEquals(largeNumOwner, subject.getOwner());
	}

	@Test
	void getsMetadata() {
		assertEquals(metadata, subject.getMetadata());
	}

	@Test
	void getsCreationTime() {
		assertEquals(timestamp, subject.getCreationTime());
	}

	@Test
	void treasuryOwnershipCheckWorks() {
		// expect:
		assertFalse(subject.isTreasuryOwned());

		// and:
		subject.setOwner(EntityId.MISSING_ENTITY_ID);
		// then:
		assertTrue(subject.isTreasuryOwned());
	}
}
