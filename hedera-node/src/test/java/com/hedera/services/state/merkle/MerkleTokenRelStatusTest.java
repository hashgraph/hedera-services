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
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.MutabilityException;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.times;

class MerkleTokenRelStatusTest {
	private final long numbers = BitPackUtils.packedNums(123, 456);
	private final long nextKey = EntityNumPair.fromLongs(123, 457).value();
	private final long prevKey = EntityNumPair.fromLongs(123, 455).value();

	private static final long balance = 666;
	private static final boolean frozen = true;
	private static final boolean kycGranted = true;
	private static final boolean automaticAssociation = false;

	private MerkleTokenRelStatus subject;

	@BeforeEach
	private void setup() {
		subject = new MerkleTokenRelStatus(balance, frozen, kycGranted,  automaticAssociation, numbers);
		subject.setNextKey(new EntityNumPair(nextKey));
		subject.setPrevKey(new EntityNumPair(prevKey));
	}

	@Test
	void objectContractMet() {
		final var one = new MerkleTokenRelStatus();
		final var two = new MerkleTokenRelStatus(balance - 1, frozen, kycGranted, automaticAssociation, numbers);
		final var three = new MerkleTokenRelStatus(balance, !frozen, kycGranted, automaticAssociation, numbers);
		final var four = new MerkleTokenRelStatus(balance, frozen, !kycGranted, automaticAssociation, numbers);
		final var five = new MerkleTokenRelStatus(balance, frozen, kycGranted, automaticAssociation, numbers - 1);
		final var six = new MerkleTokenRelStatus(balance, frozen, kycGranted, !automaticAssociation, numbers);
		final var seven = new MerkleTokenRelStatus(balance, frozen, kycGranted, automaticAssociation, numbers);
		seven.setNextKey(new EntityNumPair(nextKey));
		seven.setPrevKey(new EntityNumPair(prevKey));
		final var eight = subject;

		assertNotEquals(subject, new Object());
		assertNotEquals(subject, two);
		assertNotEquals(subject, three);
		assertNotEquals(subject, four);
		assertNotEquals(subject, five);
		assertNotEquals(subject, six);
		assertEquals(subject, seven);
		assertEquals(subject, eight);

		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(subject.hashCode(), five.hashCode());
	}

	@Test
	void merkleMethodsWork() {
		assertEquals(MerkleTokenRelStatus.CURRENT_VERSION, subject.getVersion());
		assertEquals(MerkleTokenRelStatus.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertTrue(subject.isLeaf());
	}

	@Test
	void serializeWorks() throws IOException {
		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(out);

		subject.serialize(out);

		inOrder.verify(out).writeLong(balance);
		inOrder.verify(out, times(2)).writeBoolean(true);
		inOrder.verify(out).writeLong(numbers);
		inOrder.verify(out).writeLong(nextKey);
		inOrder.verify(out).writeLong(prevKey);
	}

	@Test
	void deserializeWorksForPre0180() throws IOException {
		subject.setNextKey(new EntityNumPair(0));
		subject.setPrevKey(new EntityNumPair(0));
		final var in = mock(SerializableDataInputStream.class);
		final var defaultSubject = new MerkleTokenRelStatus();
		given(in.readLong()).willReturn(balance);
		given(in.readBoolean()).willReturn(frozen).willReturn(kycGranted).willReturn(automaticAssociation);

		defaultSubject.deserialize(in, MerkleTokenRelStatus.RELEASE_090_VERSION);

		assertNotEquals(subject, defaultSubject);

		// and when:
		defaultSubject.setKey(new EntityNumPair(numbers));

		// then:
		assertEquals(subject, defaultSubject);
	}

	@Test
	void deserializeWorksFor0180PreSdk() throws IOException {
		subject.setNextKey(new EntityNumPair(0));
		subject.setPrevKey(new EntityNumPair(0));
		final var in = mock(SerializableDataInputStream.class);
		final var defaultSubject = new MerkleTokenRelStatus();
		given(in.readLong()).willReturn(balance);
		given(in.readBoolean()).willReturn(frozen).willReturn(kycGranted).willReturn(automaticAssociation);

		defaultSubject.deserialize(in, MerkleTokenRelStatus.RELEASE_0180_PRE_SDK_VERSION);

		// then:
		assertNotEquals(subject, defaultSubject);
		// and when:
		defaultSubject.setKey(new EntityNumPair(numbers));
		// then:
		assertEquals(subject, defaultSubject);
	}

	@Test
	void deserializeWorksFor0180PostSdk() throws IOException {
		subject.setNextKey(new EntityNumPair(0));
		subject.setPrevKey(new EntityNumPair(0));
		final var in = mock(SerializableDataInputStream.class);
		final var defaultSubject = new MerkleTokenRelStatus();
		given(in.readLong()).willReturn(balance).willReturn(numbers);
		given(in.readBoolean()).willReturn(frozen).willReturn(kycGranted).willReturn(automaticAssociation);

		defaultSubject.deserialize(in, MerkleTokenRelStatus.RELEASE_0180_VERSION);

		// then:
		assertEquals(subject, defaultSubject);
	}

	@Test
	void deserializeWorksForV0240() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		final var defaultSubject = new MerkleTokenRelStatus();
		given(in.readLong())
				.willReturn(balance)
				.willReturn(numbers)
				.willReturn(nextKey)
				.willReturn(prevKey);
		given(in.readBoolean()).willReturn(frozen).willReturn(kycGranted).willReturn(automaticAssociation);

		defaultSubject.deserialize(in, MerkleTokenRelStatus.RELEASE_0240_VERSION);

		// then:
		assertEquals(subject, defaultSubject);
	}

	@Test
	void toStringWorks() {
		final var desired = "MerkleTokenRelStatus{balance=666, isFrozen=true, hasKycGranted=true, " +
				"key=528280977864 <-> (0.0.123, 0.0.456), isAutomaticAssociation=false, " +
				"nextKey=528280977865 <-> (0.0.123, 0.0.457), prevKey=528280977863 <-> (0.0.123, 0.0.455)}";
		assertEquals(desired, subject.toString());
	}

	@Test
	void copyWorks() {
		final var subjectCopy = subject.copy();

		assertNotSame(subject, subjectCopy);
		assertEquals(subject, subjectCopy);
		assertTrue(subject.isImmutable());
	}

	@Test
	void settersAndGettersWork() {
		final var subject = new MerkleTokenRelStatus();

		subject.setBalance(balance);
		subject.setFrozen(frozen);
		subject.setKycGranted(kycGranted);
		subject.setAutomaticAssociation(automaticAssociation);

		assertEquals(balance, subject.getBalance());
		assertEquals(frozen, subject.isFrozen());
		assertEquals(kycGranted, subject.isKycGranted());
		assertEquals(automaticAssociation, subject.isAutomaticAssociation());
	}

	@Test
	void cannotModifyImmutable() {
		final var subjectCopy = subject.copy();
		assertTrue(subject.isImmutable());

		assertThrows(MutabilityException.class, () -> subject.setBalance(balance+1));
		assertThrows(MutabilityException.class, () -> subject.setFrozen(!frozen));
		assertThrows(MutabilityException.class, () -> subject.setKycGranted(!kycGranted));
		assertThrows(MutabilityException.class, () -> subject.setAutomaticAssociation(!automaticAssociation));

	}

	@Test
	void deleteIsNoop() {
		assertDoesNotThrow(subject::release);
	}

	@Test
	void throwsOnNegativeBalance() {
		assertThrows(IllegalArgumentException.class, () -> subject.setBalance(-1));
	}
}
