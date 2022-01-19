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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.serdes.IoReadingFunction;
import com.hedera.services.state.serdes.IoWritingConsumer;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.MutabilityException;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.hedera.services.state.merkle.MerkleAccountState.MAX_CONCEIVABLE_TOKEN_BALANCES_SIZE;
import static com.hedera.services.state.merkle.internals.BitPackUtils.buildAutomaticAssociationMetaData;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyInt;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.times;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MerkleAccountStateTest {
	private static final JKey key = new JEd25519Key("abcdefghijklmnopqrstuvwxyz012345".getBytes());
	private static final long expiry = 1_234_567L;
	private static final long balance = 555_555L;
	private static final long autoRenewSecs = 234_567L;
	private static final long nftsOwned = 150L;
	private static final String memo = "A memo";
	private static final boolean deleted = true;
	private static final boolean smartContract = true;
	private static final boolean receiverSigRequired = true;
	private static final EntityId proxy = new EntityId(1L, 2L, 3L);
	private static final int number = 123;
	private static final int maxAutoAssociations = 1234;
	private static final int alreadyUsedAutoAssociations = 123;
	private static final int autoAssociationMetadata =
			buildAutomaticAssociationMetaData(maxAutoAssociations, alreadyUsedAutoAssociations);
	private static final Key aliasKey = Key.newBuilder()
			.setECDSASecp256K1(ByteString.copyFromUtf8("bbbbbbbbbbbbbbbbbbbbb")).build();
	private static final ByteString alias = aliasKey.getECDSASecp256K1();
	private static final ByteString otherAlias = ByteString.copyFrom("012345789".getBytes());

	private static final JKey otherKey = new JEd25519Key("aBcDeFgHiJkLmNoPqRsTuVwXyZ012345".getBytes());
	private static final long otherExpiry = 7_234_567L;
	private static final long otherBalance = 666_666L;
	private static final long otherAutoRenewSecs = 432_765L;
	private static final String otherMemo = "Another memo";
	private static final boolean otherDeleted = false;
	private static final boolean otherSmartContract = false;
	private static final boolean otherReceiverSigRequired = false;
	private static final EntityId otherProxy = new EntityId(3L, 2L, 1L);
	private static final int otherNumber = 456;
	private static final int kvPairs = 123;
	private static final int otherKvPairs = 456;

	private DomainSerdes serdes;

	private MerkleAccountState subject;

	@BeforeEach
	void setup() {
		subject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				number,
				autoAssociationMetadata,
				alias,
				kvPairs);
		serdes = mock(DomainSerdes.class);
		MerkleAccountState.serdes = serdes;
	}

	@AfterEach
	void cleanup() {
		MerkleAccountState.serdes = new DomainSerdes();
	}

	@Test
	void toStringWorks() {
		assertEquals("MerkleAccountState{number=123 <-> 0.0.123, " +
						"key=" + MiscUtils.describe(key) + ", " +
						"expiry=" + expiry + ", " +
						"balance=" + balance + ", " +
						"autoRenewSecs=" + autoRenewSecs + ", " +
						"memo=" + memo + ", " +
						"deleted=" + deleted + ", " +
						"smartContract=" + smartContract + ", " +
						"numContractKvPairs=" + kvPairs + ", "  +
						"receiverSigRequired=" + receiverSigRequired + ", " +
						"proxy=" + proxy + ", nftsOwned=0, " +
						"alreadyUsedAutoAssociations=" + alreadyUsedAutoAssociations + ", " +
						"maxAutoAssociations=" + maxAutoAssociations + ", " +
						"alias=" + alias.toStringUtf8() + "}",
				subject.toString());
	}

	@Test
	void copyIsImmutable() {
		final var key = new JKeyList();
		final var proxy = new EntityId(0, 0, 2);

		subject.copy();

		assertThrows(MutabilityException.class, () -> subject.setHbarBalance(1L));
		assertThrows(MutabilityException.class, () -> subject.setAutoRenewSecs(1_234_567L));
		assertThrows(MutabilityException.class, () -> subject.setDeleted(true));
		assertThrows(MutabilityException.class, () -> subject.setAccountKey(key));
		assertThrows(MutabilityException.class, () -> subject.setMemo("NOPE"));
		assertThrows(MutabilityException.class, () -> subject.setSmartContract(false));
		assertThrows(MutabilityException.class, () -> subject.setReceiverSigRequired(true));
		assertThrows(MutabilityException.class, () -> subject.setExpiry(1_234_567L));
		assertThrows(MutabilityException.class, () -> subject.setNumContractKvPairs(otherKvPairs));
		assertThrows(MutabilityException.class, () -> subject.setProxy(proxy));
		assertThrows(MutabilityException.class, () -> subject.setMaxAutomaticAssociations(maxAutoAssociations));
		assertThrows(MutabilityException.class,
				() -> subject.setAlreadyUsedAutomaticAssociations(alreadyUsedAutoAssociations));
	}

	@Test
	void deserializeWorksFor090Version() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		final var newSubject = new MerkleAccountState();
		subject.setAlreadyUsedAutomaticAssociations(0);
		subject.setMaxAutomaticAssociations(0);
		subject.setNumContractKvPairs(0);
		given(serdes.readNullable(argThat(in::equals), any(IoReadingFunction.class))).willReturn(key);
		given(in.readLong())
				.willReturn(expiry)
				.willReturn(balance)
				.willReturn(autoRenewSecs);
		given(in.readNormalisedString(anyInt())).willReturn(memo);
		given(in.readBoolean())
				.willReturn(deleted)
				.willReturn(smartContract)
				.willReturn(receiverSigRequired);
		given(serdes.readNullableSerializable(in)).willReturn(proxy);

		newSubject.deserialize(in, MerkleAccountState.RELEASE_090_VERSION);

		// when:
		assertNotEquals(subject, newSubject);
		newSubject.setNumber(number);
		// and then:
		assertNotEquals(subject, newSubject);
		verify(in, never()).readLongArray(MAX_CONCEIVABLE_TOKEN_BALANCES_SIZE);
		verify(in, never()).readInt();
		verify(in, times(3)).readLong();
		verify(in, never()).readByteArray(Integer.MAX_VALUE);
		// and then:
		newSubject.setAlias(alias);
		assertEquals(subject, newSubject);
	}

	@Test
	void deserializeV0160Works() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		subject.setNftsOwned(nftsOwned);
		subject.setMaxAutomaticAssociations(0);
		subject.setAlreadyUsedAutomaticAssociations(0);
		subject.setNumContractKvPairs(0);
		final var newSubject = new MerkleAccountState();
		given(serdes.readNullable(argThat(in::equals), any(IoReadingFunction.class))).willReturn(key);
		given(in.readLong())
				.willReturn(expiry)
				.willReturn(balance)
				.willReturn(autoRenewSecs)
				.willReturn(nftsOwned);
		given(in.readNormalisedString(anyInt())).willReturn(memo);
		given(in.readBoolean())
				.willReturn(deleted)
				.willReturn(smartContract)
				.willReturn(receiverSigRequired);
		given(serdes.readNullableSerializable(in)).willReturn(proxy);

		newSubject.deserialize(in, MerkleAccountState.RELEASE_0160_VERSION);

		// and when:
		assertNotEquals(subject, newSubject);
		newSubject.setNumber(number);
		// then:
		assertNotEquals(subject, newSubject);
		verify(in, never()).readLongArray(MAX_CONCEIVABLE_TOKEN_BALANCES_SIZE);
		verify(in, times(4)).readLong();
		verify(in, never()).readByteArray(Integer.MAX_VALUE);
		// and then:
		newSubject.setAlias(alias);
		assertEquals(subject, newSubject);
	}

	@Test
	void deserializeV0180WorksPreSdk() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		subject.setNftsOwned(nftsOwned);
		subject.setNumContractKvPairs(0);
		final var newSubject = new MerkleAccountState();
		given(serdes.readNullable(argThat(in::equals), any(IoReadingFunction.class))).willReturn(key);
		given(in.readLong())
				.willReturn(expiry)
				.willReturn(balance)
				.willReturn(autoRenewSecs)
				.willReturn(nftsOwned);
		given(in.readNormalisedString(anyInt())).willReturn(memo);
		given(in.readBoolean())
				.willReturn(deleted)
				.willReturn(smartContract)
				.willReturn(receiverSigRequired);
		given(in.readInt()).willReturn(autoAssociationMetadata).willReturn(number);
		given(serdes.readNullableSerializable(in)).willReturn(proxy);

		newSubject.deserialize(in, MerkleAccountState.RELEASE_0180_PRE_SDK_VERSION);

		// then:
		assertNotEquals(subject, newSubject);
		// and when:
		newSubject.setNumber(number);
		// then:
		assertNotEquals(subject, newSubject);
		// and then:
		newSubject.setAlias(alias);
		assertEquals(subject, newSubject);
	}

	@Test
	void deserializeV0180WorksPostSdk() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		subject.setNftsOwned(nftsOwned);
		subject.setNumContractKvPairs(0);
		final var newSubject = new MerkleAccountState();
		given(serdes.readNullable(argThat(in::equals), any(IoReadingFunction.class))).willReturn(key);
		given(in.readLong())
				.willReturn(expiry)
				.willReturn(balance)
				.willReturn(autoRenewSecs)
				.willReturn(nftsOwned);
		given(in.readNormalisedString(anyInt())).willReturn(memo);
		given(in.readBoolean())
				.willReturn(deleted)
				.willReturn(smartContract)
				.willReturn(receiverSigRequired);
		given(in.readInt()).willReturn(autoAssociationMetadata).willReturn(number);
		given(serdes.readNullableSerializable(in)).willReturn(proxy);

		newSubject.deserialize(in, MerkleAccountState.RELEASE_0180_VERSION);

		// then:
		assertNotEquals(subject, newSubject);
		// and then:
		newSubject.setAlias(alias);
		assertEquals(subject, newSubject);
	}

	@Test
	void deserializeV0210Works() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		subject.setNftsOwned(nftsOwned);
		subject.setNumContractKvPairs(0);
		assertEquals(0, subject.getNumContractKvPairs());
		final var newSubject = new MerkleAccountState();
		given(serdes.readNullable(argThat(in::equals), any(IoReadingFunction.class))).willReturn(key);
		given(in.readLong())
				.willReturn(expiry)
				.willReturn(balance)
				.willReturn(autoRenewSecs)
				.willReturn(nftsOwned);
		given(in.readNormalisedString(anyInt())).willReturn(memo);
		given(in.readBoolean())
				.willReturn(deleted)
				.willReturn(smartContract)
				.willReturn(receiverSigRequired);
		given(in.readInt()).willReturn(autoAssociationMetadata).willReturn(number);
		given(serdes.readNullableSerializable(in)).willReturn(proxy);
		given(in.readByteArray(Integer.MAX_VALUE)).willReturn(alias.toByteArray());

		newSubject.deserialize(in, MerkleAccountState.RELEASE_0210_VERSION);

		// then:
		assertEquals(subject, newSubject);
	}

	@Test
	void deserializeV0220Works() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		subject.setNftsOwned(nftsOwned);
		final var newSubject = new MerkleAccountState();
		given(serdes.readNullable(argThat(in::equals), any(IoReadingFunction.class))).willReturn(key);
		given(in.readLong())
				.willReturn(expiry)
				.willReturn(balance)
				.willReturn(autoRenewSecs)
				.willReturn(nftsOwned);
		given(in.readNormalisedString(anyInt())).willReturn(memo);
		given(in.readBoolean())
				.willReturn(deleted)
				.willReturn(smartContract)
				.willReturn(receiverSigRequired);
		given(in.readInt())
				.willReturn(autoAssociationMetadata)
				.willReturn(number)
				.willReturn(kvPairs);
		given(serdes.readNullableSerializable(in)).willReturn(proxy);
		given(in.readByteArray(Integer.MAX_VALUE)).willReturn(alias.toByteArray());

		newSubject.deserialize(in, MerkleAccountState.RELEASE_0220_VERSION);

		// then:
		assertEquals(subject, newSubject);
	}

	@Test
	void serializeWorks() throws IOException {
		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(serdes, out);

		subject.serialize(out);

		inOrder.verify(serdes).writeNullable(argThat(key::equals), argThat(out::equals), any(IoWritingConsumer.class));
		inOrder.verify(out).writeLong(expiry);
		inOrder.verify(out).writeLong(balance);
		inOrder.verify(out).writeLong(autoRenewSecs);
		inOrder.verify(out).writeNormalisedString(memo);
		inOrder.verify(out, times(3)).writeBoolean(true);
		inOrder.verify(serdes).writeNullableSerializable(proxy, out);
		verify(out, never()).writeLongArray(any());
		inOrder.verify(out).writeInt(number);
		inOrder.verify(out).writeByteArray(alias.toByteArray());
		inOrder.verify(out).writeInt(kvPairs);
	}

	@Test
	void copyWorks() {
		final var copySubject = subject.copy();

		assertNotSame(copySubject, subject);
		assertEquals(subject, copySubject);
	}

	@Test
	void equalsWorksWithRadicalDifferences() {
		final var identical = subject;

		// expect:
		assertEquals(subject, identical);
		assertNotEquals(null, subject);
		assertNotEquals(subject, new Object());
	}

	@Test
	void equalsWorksForKey() {
		final var otherSubject = new MerkleAccountState(
				otherKey,
				expiry, balance, autoRenewSecs,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				number,
				autoAssociationMetadata,
				alias,
				kvPairs);

		assertNotEquals(subject, otherSubject);
	}

	@Test
	void equalsWorksForExpiry() {
		final var otherSubject = new MerkleAccountState(
				key,
				otherExpiry, balance, autoRenewSecs,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				number,
				autoAssociationMetadata,
				alias,
				kvPairs);

		assertNotEquals(subject, otherSubject);
	}

	@Test
	void equalsWorksForBalance() {
		final var otherSubject = new MerkleAccountState(
				key,
				expiry, otherBalance, autoRenewSecs,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				number,
				autoAssociationMetadata,
				alias,
				kvPairs);

		assertNotEquals(subject, otherSubject);
	}

	@Test
	void equalsWorksForAutoRenewSecs() {
		final var otherSubject = new MerkleAccountState(
				key,
				expiry, balance, otherAutoRenewSecs,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				number,
				autoAssociationMetadata,
				alias,
				kvPairs);

		assertNotEquals(subject, otherSubject);
	}

	@Test
	void equalsWorksForMemo() {
		final var otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs,
				otherMemo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				number,
				autoAssociationMetadata,
				alias,
				kvPairs);

		assertNotEquals(subject, otherSubject);
	}

	@Test
	void equalsWorksForDeleted() {
		final var otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs,
				memo,
				otherDeleted, smartContract, receiverSigRequired,
				proxy,
				number,
				autoAssociationMetadata,
				alias,
				kvPairs);

		assertNotEquals(subject, otherSubject);
	}

	@Test
	void equalsWorksForSmartContract() {
		final var otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs,
				memo,
				deleted, otherSmartContract, receiverSigRequired,
				proxy,
				number,
				autoAssociationMetadata,
				alias,
				kvPairs);

		assertNotEquals(subject, otherSubject);
	}

	@Test
	void equalsWorksForReceiverSigRequired() {
		final var otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs,
				memo,
				deleted, smartContract, otherReceiverSigRequired,
				proxy,
				number,
				autoAssociationMetadata,
				alias,
				kvPairs);

		assertNotEquals(subject, otherSubject);
	}

	@Test
	void equalsWorksForNumber() {
		final var otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				otherNumber,
				autoAssociationMetadata,
				alias,
				kvPairs);

		assertNotEquals(subject, otherSubject);
	}

	@Test
	void equalsWorksForProxy() {
		final var otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs,
				memo,
				deleted, smartContract, receiverSigRequired,
				otherProxy,
				number, autoAssociationMetadata,
				alias,
				kvPairs);

		assertNotEquals(subject, otherSubject);
	}

	@Test
	void equalsWorksForAlias() {
		final var otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				number, autoAssociationMetadata,
				otherAlias,
				kvPairs);

		assertNotEquals(subject, otherSubject);
	}

	@Test
	void equalsWorksForKvPairs() {
		final var otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				number, autoAssociationMetadata,
				alias,
				otherKvPairs);

		assertNotEquals(subject, otherSubject);
	}

	@Test
	void merkleMethodsWork() {
		assertEquals(MerkleAccountState.RELEASE_0220_VERSION, subject.getVersion());
		assertEquals(MerkleAccountState.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertTrue(subject.isLeaf());
	}

	@Test
	void objectContractMet() {
		final var defaultSubject = new MerkleAccountState();
		final var identicalSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				number,
				autoAssociationMetadata,
				alias,
				kvPairs);

		final var otherSubject = new MerkleAccountState(
				otherKey,
				otherExpiry, otherBalance, otherAutoRenewSecs,
				otherMemo,
				otherDeleted, otherSmartContract, otherReceiverSigRequired,
				otherProxy,
				otherNumber,
				autoAssociationMetadata,
				alias,
				otherKvPairs);

		assertNotEquals(subject.hashCode(), defaultSubject.hashCode());
		assertNotEquals(subject.hashCode(), otherSubject.hashCode());
		assertEquals(subject.hashCode(), identicalSubject.hashCode());
	}

	@Test
	void autoAssociationMetadataWorks() {
		final int max = 12;
		final int used = 5;
		final var defaultSubject = new MerkleAccountState();
		defaultSubject.setMaxAutomaticAssociations(max);
		defaultSubject.setAlreadyUsedAutomaticAssociations(used);

		assertEquals(used, defaultSubject.getAlreadyUsedAutomaticAssociations());
		assertEquals(max, defaultSubject.getMaxAutomaticAssociations());

		var toIncrement = defaultSubject.getAlreadyUsedAutomaticAssociations();
		toIncrement++;

		defaultSubject.setAlreadyUsedAutomaticAssociations(toIncrement);
		assertEquals(toIncrement, defaultSubject.getAlreadyUsedAutomaticAssociations());

		var changeMax = max + 10;
		defaultSubject.setMaxAutomaticAssociations(changeMax);

		assertEquals(changeMax, defaultSubject.getMaxAutomaticAssociations());
	}
}
