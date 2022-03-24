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
import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.MutabilityException;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

import static com.hedera.services.state.merkle.MerkleAccountState.MAX_CONCEIVABLE_TOKEN_BALANCES_SIZE;
import static com.hedera.services.state.merkle.internals.BitPackUtils.buildAutomaticAssociationMetaData;
import static com.hedera.services.state.merkle.internals.BitPackUtils.numFromCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
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
	private static final UInt256 firstKey =
			UInt256.fromHexString("0x0000fe0432ce31138ecf09aa3e8a410004a1e204ef84efe01ee160fea1e22060");
	private static final int[] explicitFirstKey = ContractKey.asPackedInts(firstKey);
	private static final byte numNonZeroBytesInFirst = 30;

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
	private static final UInt256 otherFirstKey =
			UInt256.fromHexString("0x0011fe0432ce31138ecf09aa3e8a410004bbe204ef84efe01ee160febbe22060");
	private static final int[] otherExplicitFirstKey = ContractKey.asPackedInts(otherFirstKey);
	private static final byte otherNumNonZeroBytesInFirst = 31;

	private static final EntityNum spenderNum = EntityNum.fromLong(1000L);
	private static final EntityNum tokenForAllowance = EntityNum.fromLong(2000L);
	private static final Long cryptoAllowance = 10L;
	private static final boolean approvedForAll = false;
	private static final Long tokenAllowanceVal = 1L;
	private static final List<Long> serialNumbers = List.of(1L, 2L);

	private static final FcTokenAllowanceId tokenAllowanceKey = FcTokenAllowanceId.from(tokenForAllowance, spenderNum);
	private static final FcTokenAllowance tokenAllowanceValue = FcTokenAllowance.from(approvedForAll, serialNumbers);

	private TreeMap<EntityNum, Long> cryptoAllowances = new TreeMap<>();
	private TreeMap<FcTokenAllowanceId, Long> fungibleTokenAllowances = new TreeMap<>();
	private TreeMap<FcTokenAllowanceId, FcTokenAllowance> nftAllowances = new TreeMap<>();

	TreeMap<EntityNum, Long> otherCryptoAllowances = new TreeMap<>();
	TreeMap<FcTokenAllowanceId, Long> otherFungibleTokenAllowances = new TreeMap<>();
	private TreeMap<FcTokenAllowanceId, FcTokenAllowance> otherNftAllowances = new TreeMap<>();

	private DomainSerdes serdes;

	private MerkleAccountState subject;

	@BeforeEach
	void setup() {
		cryptoAllowances.put(spenderNum, cryptoAllowance);
		nftAllowances.put(tokenAllowanceKey, tokenAllowanceValue);
		fungibleTokenAllowances.put(tokenAllowanceKey, tokenAllowanceVal);

		subject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				number,
				autoAssociationMetadata,
				alias,
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				nftAllowances,
				explicitFirstKey,
				numNonZeroBytesInFirst);
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
						"numContractKvPairs=" + kvPairs + ", " +
						"receiverSigRequired=" + receiverSigRequired + ", " +
						"proxy=" + proxy + ", nftsOwned=0, " +
						"alreadyUsedAutoAssociations=" + alreadyUsedAutoAssociations + ", " +
						"maxAutoAssociations=" + maxAutoAssociations + ", " +
						"alias=" + alias.toStringUtf8() + ", " +
						"cryptoAllowances=" + cryptoAllowances + ", " +
						"fungibleTokenAllowances=" + fungibleTokenAllowances + ", " +
						"firstContractStorageKey=fe0432ce31138ecf09aa3e8a41004a1e204ef84efe01ee160fea1e22060, " +
						"nftAllowances=" + nftAllowances + "}",
				subject.toString());
	}

	@Test
	void getterWorksForNullStorageKey() {
		subject.setFirstUint256Key(null);
		assertNull(subject.getFirstContractStorageKey());
		assertEquals(0, subject.getFirstUint256KeyNonZeroBytes());
	}

	@Test
	void getterWorksForTypedStorageKey() {
		final var expected = new ContractKey(numFromCode(number), explicitFirstKey);
		final var actual = subject.getFirstContractStorageKey();
		assertEquals(expected, actual);
	}

	@Test
	void settingFirstKeyUpdatesNonZeroBytes() {
		subject.setFirstUint256Key(otherExplicitFirstKey);
		assertEquals(otherNumNonZeroBytesInFirst, subject.getFirstUint256KeyNonZeroBytes());
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
		assertThrows(MutabilityException.class, () -> subject.setCryptoAllowances(cryptoAllowances));
		assertThrows(MutabilityException.class, () -> subject.setNftAllowances(nftAllowances));
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
		setEmptyAllowances();
		subject.setFirstUint256Key(null);
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
		subject.setFirstUint256Key(null);
		setEmptyAllowances();
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
		setEmptyAllowances();
		subject.setFirstUint256Key(null);
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
		setEmptyAllowances();
		subject.setFirstUint256Key(null);
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
		setEmptyAllowances();
		subject.setFirstUint256Key(null);
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
		setEmptyAllowances();
		subject.setFirstUint256Key(null);
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
	void deserializeV0230Works() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		subject.setNftsOwned(nftsOwned);
		subject.setFirstUint256Key(null);
		final var newSubject = new MerkleAccountState();
		given(serdes.readNullable(argThat(in::equals), any(IoReadingFunction.class))).willReturn(key);
		given(in.readLong())
				.willReturn(expiry)
				.willReturn(balance)
				.willReturn(autoRenewSecs)
				.willReturn(nftsOwned)
				.willReturn(spenderNum.longValue())
				.willReturn(cryptoAllowance)
				.willReturn(tokenAllowanceVal);
		given(in.readNormalisedString(anyInt())).willReturn(memo);
		given(in.readBoolean())
				.willReturn(deleted)
				.willReturn(smartContract)
				.willReturn(receiverSigRequired)
				.willReturn(approvedForAll);
		given(in.readInt())
				.willReturn(autoAssociationMetadata)
				.willReturn(number)
				.willReturn(kvPairs)
				.willReturn(cryptoAllowances.size())
				.willReturn(fungibleTokenAllowances.size())
				.willReturn(nftAllowances.size());
		given(serdes.readNullableSerializable(in)).willReturn(proxy);
		given(in.readByteArray(Integer.MAX_VALUE)).willReturn(alias.toByteArray());
		given(in.readSerializable())
				.willReturn(tokenAllowanceKey)
				.willReturn(tokenAllowanceKey)
				.willReturn(tokenAllowanceValue);
		given(in.readLongList(Integer.MAX_VALUE))
				.willReturn(serialNumbers);

		newSubject.deserialize(in, MerkleAccountState.RELEASE_0230_VERSION);

		// then:
		assertEquals(subject, newSubject);
	}

	@Test
	void deserializeV0250WorksForContract() throws IOException, ConstructableRegistryException {
		MerkleAccountState.serdes = new DomainSerdes();
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(EntityId.class, EntityId::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FcTokenAllowance.class, FcTokenAllowance::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FcTokenAllowanceId.class, FcTokenAllowanceId::new));
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final var dos = new SerializableDataOutputStream(baos);
		subject.serialize(dos);
		dos.flush();
		final var bytes = baos.toByteArray();
		final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		final var din = new SerializableDataInputStream(bais);

		final var newSubject = new MerkleAccountState();
		newSubject.deserialize(din, MerkleAccountState.RELEASE_0250_VERSION);

		assertEquals(subject, newSubject);
	}

	@Test
	void deserializeV0240WorksForNonContract() throws IOException, ConstructableRegistryException {
		MerkleAccountState.serdes = new DomainSerdes();
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(EntityId.class, EntityId::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FcTokenAllowance.class, FcTokenAllowance::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FcTokenAllowanceId.class, FcTokenAllowanceId::new));
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final var dos = new SerializableDataOutputStream(baos);
		subject.setSmartContract(false);
		subject.setFirstUint256Key(null);
		subject.serialize(dos);
		dos.flush();
		final var bytes = baos.toByteArray();
		final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		final var din = new SerializableDataInputStream(bais);

		final var newSubject = new MerkleAccountState();
		newSubject.deserialize(din, MerkleAccountState.RELEASE_0240_VERSION);

		assertEquals(subject, newSubject);
	}

	@Test
	void serializeWorksForContract() throws IOException {
		final var rawFirstKey = firstKey.toArrayUnsafe();
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

		inOrder.verify(out).writeInt(cryptoAllowances.size());
		inOrder.verify(out).writeLong(spenderNum.longValue());
		inOrder.verify(out).writeLong(cryptoAllowance);

		inOrder.verify(out).writeInt(fungibleTokenAllowances.size());
		inOrder.verify(out).writeSerializable(tokenAllowanceKey, true);
		inOrder.verify(out).writeLong(tokenAllowanceVal);

		inOrder.verify(out).writeInt(nftAllowances.size());
		inOrder.verify(out).writeSerializable(tokenAllowanceKey, true);
		inOrder.verify(out).writeSerializable(tokenAllowanceValue, true);

		inOrder.verify(out).write(numNonZeroBytesInFirst);
		for (int i = 0, offset = 32 - numNonZeroBytesInFirst; i < numNonZeroBytesInFirst; i++) {
			inOrder.verify(out).write(rawFirstKey[i + offset]);
		}
	}

	@Test
	void serializeWorksForNonContract() throws IOException {
		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(serdes, out);
		subject.setSmartContract(false);

		subject.serialize(out);

		inOrder.verify(serdes).writeNullable(argThat(key::equals), argThat(out::equals), any(IoWritingConsumer.class));
		inOrder.verify(out).writeLong(expiry);
		inOrder.verify(out).writeLong(balance);
		inOrder.verify(out).writeLong(autoRenewSecs);
		inOrder.verify(out).writeNormalisedString(memo);
		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(out).writeBoolean(false);
		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(serdes).writeNullableSerializable(proxy, out);
		verify(out, never()).writeLongArray(any());
		inOrder.verify(out).writeInt(number);
		inOrder.verify(out).writeByteArray(alias.toByteArray());

		inOrder.verify(out).writeInt(cryptoAllowances.size());
		inOrder.verify(out).writeLong(spenderNum.longValue());
		inOrder.verify(out).writeLong(cryptoAllowance);

		inOrder.verify(out).writeInt(fungibleTokenAllowances.size());
		inOrder.verify(out).writeSerializable(tokenAllowanceKey, true);
		inOrder.verify(out).writeLong(tokenAllowanceVal);

		inOrder.verify(out).writeInt(nftAllowances.size());
		inOrder.verify(out).writeSerializable(tokenAllowanceKey, true);
		inOrder.verify(out).writeSerializable(tokenAllowanceValue, true);

		verify(out, never()).writeByte(numNonZeroBytesInFirst);
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
	void equalsWorksForFirstKeyBytes() {
		final var otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				number,
				autoAssociationMetadata,
				alias,
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				nftAllowances,
				otherExplicitFirstKey,
				otherNumNonZeroBytesInFirst);

		assertNotEquals(subject, otherSubject);
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
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				nftAllowances,
				explicitFirstKey,
				numNonZeroBytesInFirst);

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
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				nftAllowances,
				explicitFirstKey,
				numNonZeroBytesInFirst);

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
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				nftAllowances,
				explicitFirstKey,
				numNonZeroBytesInFirst);

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
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				nftAllowances,
				explicitFirstKey,
				numNonZeroBytesInFirst);

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
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				nftAllowances,
				explicitFirstKey,
				numNonZeroBytesInFirst);

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
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				nftAllowances,
				explicitFirstKey,
				numNonZeroBytesInFirst);

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
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				nftAllowances,
				explicitFirstKey,
				numNonZeroBytesInFirst);

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
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				nftAllowances,
				explicitFirstKey,
				numNonZeroBytesInFirst);

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
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				nftAllowances,
				explicitFirstKey,
				numNonZeroBytesInFirst);

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
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				nftAllowances,
				explicitFirstKey,
				numNonZeroBytesInFirst);

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
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				nftAllowances,
				explicitFirstKey,
				numNonZeroBytesInFirst);

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
				otherKvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				nftAllowances,
				explicitFirstKey,
				numNonZeroBytesInFirst);

		assertNotEquals(subject, otherSubject);
	}

	@Test
	void equalsWorksForAllowances() {
		final EntityNum spenderNum = EntityNum.fromLong(100L);
		final EntityNum tokenForAllowance = EntityNum.fromLong(200L);
		final Long cryptoAllowance = 100L;
		final boolean approvedForAll = true;
		final Long tokenAllowanceVal = 1L;
		final List<Long> serialNumbers = new ArrayList<>();

		final FcTokenAllowanceId tokenAllowanceKey = FcTokenAllowanceId.from(tokenForAllowance, spenderNum);
		final FcTokenAllowance tokenAllowanceValue = FcTokenAllowance.from(approvedForAll, serialNumbers);

		otherCryptoAllowances.put(spenderNum, cryptoAllowance);
		otherNftAllowances.put(tokenAllowanceKey, tokenAllowanceValue);
		otherFungibleTokenAllowances.put(tokenAllowanceKey, tokenAllowanceVal);

		final var otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				number, autoAssociationMetadata,
				alias,
				otherKvPairs,
				otherCryptoAllowances,
				otherFungibleTokenAllowances,
				otherNftAllowances,
				explicitFirstKey,
				numNonZeroBytesInFirst);

		assertNotEquals(subject, otherSubject);
	}

	@Test
	void merkleMethodsWork() {
		assertEquals(MerkleAccountState.RELEASE_0250_VERSION, subject.getVersion());
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
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				nftAllowances,
				explicitFirstKey,
				numNonZeroBytesInFirst);

		final var otherSubject = new MerkleAccountState(
				otherKey,
				otherExpiry, otherBalance, otherAutoRenewSecs,
				otherMemo,
				otherDeleted, otherSmartContract, otherReceiverSigRequired,
				otherProxy,
				otherNumber,
				autoAssociationMetadata,
				alias,
				otherKvPairs,
				otherCryptoAllowances,
				otherFungibleTokenAllowances,
				otherNftAllowances,
				otherExplicitFirstKey,
				otherNumNonZeroBytesInFirst);

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

	@Test
	void addingAndRemovingToMapsWork() {
		final EntityNum spenderNum = EntityNum.fromLong(2000L);
		final EntityNum tokenForAllowance = EntityNum.fromLong(4000L);
		final Long cryptoAllowance = 100L;
		final boolean approvedForAll = false;
		final Long tokenAllowanceVal = 10L;
		final List<Long> serialNumbers = List.of(3L, 4L);
		final FcTokenAllowanceId tokenAllowanceKey = FcTokenAllowanceId.from(tokenForAllowance, spenderNum);

		subject.addCryptoAllowance(spenderNum, cryptoAllowance);
		subject.addFungibleTokenAllowance(tokenForAllowance, spenderNum, tokenAllowanceVal);
		subject.addNftAllowance(tokenForAllowance, spenderNum, approvedForAll, serialNumbers);
		assertTrue(subject.getCryptoAllowances().containsKey(spenderNum));
		assertTrue(subject.getNftAllowances().containsKey(tokenAllowanceKey));
		assertEquals(2, subject.getCryptoAllowances().size());
		assertEquals(2, subject.getNftAllowances().size());

		subject.removeCryptoAllowance(spenderNum);
		subject.removeFungibleTokenAllowance(tokenForAllowance, spenderNum);
		subject.removeNftAllowance(tokenForAllowance, spenderNum);

		assertFalse(subject.getCryptoAllowances().containsKey(spenderNum));
		assertFalse(subject.getNftAllowances().containsKey(tokenAllowanceKey));
		assertEquals(1, subject.getCryptoAllowances().size());
		assertEquals(1, subject.getNftAllowances().size());
	}

	private void setEmptyAllowances() {
		subject.setCryptoAllowances(new TreeMap<>());
		subject.setFungibleTokenAllowances(new TreeMap<>());
		subject.setNftAllowances(new TreeMap<>());
	}

	@Test
	void gettersForAllowancesWork() {
		var subject = new MerkleAccountState();
		assertEquals(Collections.emptyMap(), subject.getCryptoAllowances());
		assertEquals(Collections.emptyMap(), subject.getFungibleTokenAllowances());
		assertEquals(Collections.emptyMap(), subject.getNftAllowances());
	}

	@Test
	void settersForAllowancesWork() {
		var subject = new MerkleAccountState();
		subject.setCryptoAllowances(cryptoAllowances);
		subject.setFungibleTokenAllowances(fungibleTokenAllowances);
		subject.setNftAllowances(nftAllowances);
		assertEquals(cryptoAllowances, subject.getCryptoAllowances());
		assertEquals(fungibleTokenAllowances, subject.getFungibleTokenAllowances());
		assertEquals(nftAllowances, subject.getNftAllowances());
	}

}
