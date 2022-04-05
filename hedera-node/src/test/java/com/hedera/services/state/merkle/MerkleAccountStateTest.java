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
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.MutabilityException;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.hedera.services.state.merkle.MerkleAccountState.MAX_CONCEIVABLE_TOKEN_BALANCES_SIZE;
import static com.hedera.services.state.merkle.internals.BitPackUtils.buildAutomaticAssociationMetaData;
import static com.hedera.services.store.models.Id.MISSING_ID;
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
	private static final int usedAutoAssociations = 1233;
	private static final int autoAssociationMetadata =
			buildAutomaticAssociationMetaData(maxAutoAssociations, usedAutoAssociations);
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
	private static final int associatedTokensCount = 3;
	private static final int numPositiveBalances = 2;

	private static final EntityNum spenderNum1 = EntityNum.fromLong(1000L);
	private static final EntityNum spenderNum2 = EntityNum.fromLong(3000L);
	private static final EntityNum tokenForAllowance = EntityNum.fromLong(2000L);
	private static final long headTokenNum = tokenForAllowance.longValue();
	private static final Long cryptoAllowance = 10L;
	private static final boolean approvedForAll = false;
	private static final Long tokenAllowanceVal = 1L;
	private static final List<Long> serialNumbers = List.of(1L, 2L);

	private static final FcTokenAllowanceId tokenAllowanceKey1 = FcTokenAllowanceId.from(tokenForAllowance, spenderNum1);
	private static final FcTokenAllowanceId tokenAllowanceKey2 = FcTokenAllowanceId.from(tokenForAllowance, spenderNum2);
	private static final FcTokenAllowance tokenAllowanceValue = FcTokenAllowance.from(approvedForAll, serialNumbers);

	private TreeMap<EntityNum, Long> cryptoAllowances = new TreeMap<>();
	private TreeMap<FcTokenAllowanceId, Long> fungibleTokenAllowances = new TreeMap<>();
	private TreeSet<FcTokenAllowanceId> approveForAllNfts = new TreeSet<>();

	TreeMap<EntityNum, Long> otherCryptoAllowances = new TreeMap<>();
	TreeMap<FcTokenAllowanceId, Long> otherFungibleTokenAllowances = new TreeMap<>();
	TreeSet<FcTokenAllowanceId> otherApproveForAllNfts = new TreeSet<>();

	private DomainSerdes serdes;

	private MerkleAccountState subject;

	@BeforeEach
	void setup() {
		cryptoAllowances.put(spenderNum1, cryptoAllowance);
		approveForAllNfts.add(tokenAllowanceKey2);
		fungibleTokenAllowances.put(tokenAllowanceKey1, tokenAllowanceVal);

		subject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				number,
				maxAutoAssociations,
				usedAutoAssociations,
				alias,
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				approveForAllNfts,
				associatedTokensCount,
				numPositiveBalances,
				headTokenNum);
		serdes = mock(DomainSerdes.class);
		MerkleAccountState.serdes = serdes;
	}

	@AfterEach
	void cleanup() {
		MerkleAccountState.serdes = new DomainSerdes();
	}

//	@Test
//	void canDeserialize024xStates() throws IOException, ConstructableRegistryException {
//		MerkleAccountState.serdes = new DomainSerdes();
//		ConstructableRegistry.registerConstructable(
//				new ClassConstructorPair(EntityId.class, EntityId::new));
//
//		final var r = new SplittableRandom(4_242_424);
//		final var num024xStates = 5;
//		// Serialized on the v0.24.1 tag using TxnUtils#serializeToHex()
//		final var hexed024xSerializedForms = new String[] {
//				"0100000000000000020000000000eefa560000000000000021ecbd0164c7e14ea4dfc65fb5a41b7b496d197a616f3890c9cf98c1f9941dbd44f87880b534d4ed213f056d21236f5d9934694c03bb0b91addc000000645b4162646e68380369423f1c26107c6a6169621134177b5c1b412f6458546b31240e35784a16517b442e4f73441d7b4d64320b481d166d1318691a514a3b7b793d1b48740c4150114a1538060c240d552953786d6f373756232978187c3801444d5c295600000101f35ba643324efa370000000142dec2d45bf6488e3be6b268d29cdcaf687c1ce5e74d43e200000000000000001a53943eb5b28b1800000024a859534a7dc1f7d50d74e54ae9191ec2ad7374a9804801009be71f46c090440b3893482b75597cc6000000000000000000000000",
//				"0100000000000000020000000000ecf2ea0000000000000020c09c3631dd99bfc3402344dd5538dedf8ab233e251c34e79ca4fec686f859441429d7eeca015d4da0b273bbad05c1c2808ca51a7910de14b00000064047a3a3452137559202905745a5b0f7270310d0e00436a55622c39246c195f40414d113464172a10196b1d553e120a753f11521b527e5a7326511166727869661f227e50164c2d4533073f41233910323b6c5d4b2e7946786c4c0464093d62011a44383b00010001f35ba643324efa37000000011e0edbd00fcce0901fd4d02967db2dc474a7df70a6c9377700000000000000007e144b2a82128f7a00000024d4295dc5b304c82f0e40b56d276f657af230a9c98953db3b479095d3a552c36343b9bb8b6f41f8ac000000000000000000000000",
//				"0100000000000000020000000000ef843a0000000000000024565456a617c70d53752f3aec85623226c721dc2add366bec0dca4a06e5476314013aaff869dfcd95d5b653517432b2dc9ee26d0215eae21462de5b8800000064420f4b4a4d146f4d6d62022e0d15290972010e4c04677760004018703a62616a6909411c6c7238151d302a322d22751f4c0d063e61773e1d2c197e2914442331015b641037712d6c0a026f232314376b1c52174b3d35682c517157486137194e0959231800000001f35ba643324efa37000000014d45530b3ebbfc795e6735621760f9be3001ed8477f5ea5900000000000000007d88ed2dd4dc1ade000000241945caf6e10db757da1b820da3254237c4dc243544e172102079e0530ca26cb021e42bf834c37248000000000000000000000000",
//				"0100000000000000020000000000ecb1f000000000000000780000000200000000000000020000000000ef843a00000000000000244b7ada2a1fc3469001efca4b4e7dda1a34ea7e6396d6afdfde873d4df57c136a950e082a00000000000000020000000000ecf2ea0000000000000020df4ddb8efc34c44d33dce3576d60262e04d19b1a424472b4b5f06c5cf028c75b3eef19647108bbb67371b138ae278551117a9d528e701b1200000064722f660112322137047c032e5d6e06297a74091c0d35585a241d405615436e744d36205913085a0e25165f21032d646c7d575d6c1041350a2e001212032e4b28371645795f1f045d6f495726040a1a56462705027c33616e3c45295e70323950681b435100000101f35ba643324efa37000000012ce0291fcf1ddd122b26e628e59ad4d81f440e71c0f55a3b00000000000000003cdb87ee63c68e7500000024e7ef1b0e049e8b4652e949799b3609a5d6a0e2fe2eead8b1fcd16955460c5b25e04cc3cf4e82e4df000000000000000000000000",
//				"0100000000000000020000000000ed33e400000000000000181be08087d85ced322940a1ea80f22bfa1a44e90beef821d325a8a48bfe1752c12f6310f856e1dcbb70a7cc375d760ad9000000643e3a7c17393d174951764d5f1964581f450b3c7a3a774e66070c097770640e095d68092a576d75036b694977484e782e430a530a127c1b527a500f20211740480a493d23295a4f2e32162a38460f6a3b4804680346066f2f055e0f3c7a1a246b5169166801000101f35ba643324efa37000000012c1939ccac5090c47e1cdcb7e97dbb5959488af38801dc6b000000000000000041fb2a015b28281c00000024fa0be1cb308140ec3f776d2606a8e52ba93f3c2e7538a22cefb065013fd9384df74e4dcf09058d9f000000000000000000000000",
//		};
//		for (int i = 0; i < num024xStates; i++) {
//			final var seededState = new MerkleAccountState(
//					keyFrom(r),
//					unsignedLongFrom(r),
//					unsignedLongFrom(r),
//					unsignedLongFrom(r),
//					stringFrom(r, 100),
//					r.nextBoolean(),
//					r.nextBoolean(),
//					r.nextBoolean(),
//					entityIdFrom(r),
//					r.nextInt(),
//					unsignedIntFrom(r),
//					unsignedIntFrom(r),
//					byteStringFrom(r, 36),
//					unsignedIntFrom(r),
//					// This migration relies on the fact that no production state ever included allowances
//					Collections.emptyMap(),
//					Collections.emptyMap(),
//					Collections.emptySet(),
//					0,
//					0,
//					0);
//			final var actualState = deserializeFromHex(
//					MerkleAccountState::new,
//					MerkleAccountState.RELEASE_0230_VERSION,
//					hexed024xSerializedForms[i]);
//			assertEquals(seededState, actualState);
//		}
//	}

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
						"alreadyUsedAutoAssociations=" + usedAutoAssociations + ", " +
						"maxAutoAssociations=" + maxAutoAssociations + ", " +
						"alias=" + alias.toStringUtf8() + ", " +
						"cryptoAllowances=" + cryptoAllowances + ", " +
						"fungibleTokenAllowances=" + fungibleTokenAllowances + ", " +
						"approveForAllNfts=" + approveForAllNfts + ", " +
						"numAssociations=" + associatedTokensCount + ", " +
						"numPositiveBalances=" + numPositiveBalances + ", " +
						"headTokenId=" + headTokenNum + "}",
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
		assertThrows(MutabilityException.class, () -> subject.setCryptoAllowances(cryptoAllowances));
		assertThrows(MutabilityException.class, () -> subject.setApproveForAllNfts(approveForAllNfts));
		assertThrows(MutabilityException.class,
				() -> subject.setUsedAutomaticAssociations(usedAutoAssociations));
	}

	@Test
	void deserializeWorksFor090Version() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		final var newSubject = new MerkleAccountState();
		subject.setUsedAutomaticAssociations(0);
		subject.setMaxAutomaticAssociations(0);
		subject.setNumContractKvPairs(0);
		subject.setHeadTokenId(MISSING_ID.num());
		subject.setNumPositiveBalances(0);
		subject.setNumAssociations(0);
		setEmptyAllowances();
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
		subject.setUsedAutomaticAssociations(0);
		subject.setNumContractKvPairs(0);
		subject.setHeadTokenId(MISSING_ID.num());
		subject.setNumPositiveBalances(0);
		subject.setNumAssociations(0);
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
		subject.setHeadTokenId(MISSING_ID.num());
		subject.setNumPositiveBalances(0);
		subject.setNumAssociations(0);
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
		subject.setHeadTokenId(MISSING_ID.num());
		subject.setNumPositiveBalances(0);
		subject.setNumAssociations(0);
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
		subject.setHeadTokenId(MISSING_ID.num());
		subject.setNumPositiveBalances(0);
		subject.setNumAssociations(0);
		setEmptyAllowances();
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
		subject.setHeadTokenId(MISSING_ID.num());
		subject.setNumPositiveBalances(0);
		subject.setNumAssociations(0);
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
		subject.setHeadTokenId(MISSING_ID.num());
		subject.setNumPositiveBalances(0);
		subject.setNumAssociations(0);
		final var newSubject = new MerkleAccountState();
		given(serdes.readNullable(argThat(in::equals), any(IoReadingFunction.class))).willReturn(key);
		given(in.readLong())
				.willReturn(expiry)
				.willReturn(balance)
				.willReturn(autoRenewSecs)
				.willReturn(nftsOwned)
				.willReturn(spenderNum1.longValue())
				.willReturn(cryptoAllowance)
				.willReturn(tokenAllowanceVal);
		given(in.readNormalisedString(anyInt())).willReturn(memo);
		given(in.readBoolean())
				.willReturn(deleted)
				.willReturn(smartContract)
				.willReturn(receiverSigRequired);
		given(in.readInt())
				.willReturn(autoAssociationMetadata)
				.willReturn(number)
				.willReturn(kvPairs)
				.willReturn(cryptoAllowances.size())
				.willReturn(fungibleTokenAllowances.size())
				.willReturn(approveForAllNfts.size());
		given(serdes.readNullableSerializable(in)).willReturn(proxy);
		given(in.readByteArray(Integer.MAX_VALUE)).willReturn(alias.toByteArray());
		given(in.readSerializable())
				.willReturn(tokenAllowanceKey1)
				.willReturn(tokenAllowanceKey2)
				.willReturn(tokenAllowanceValue);
		given(in.readLongList(Integer.MAX_VALUE))
				.willReturn(serialNumbers);

		newSubject.deserialize(in, MerkleAccountState.RELEASE_0230_VERSION);

		// then:
		assertEquals(subject, newSubject);
	}

	@Test
	void deserializeV0250Works() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		subject.setNftsOwned(nftsOwned);
		final var newSubject = new MerkleAccountState();
		given(serdes.readNullable(argThat(in::equals), any(IoReadingFunction.class))).willReturn(key);
		given(in.readLong())
				.willReturn(expiry)
				.willReturn(balance)
				.willReturn(autoRenewSecs)
				.willReturn(nftsOwned)
				.willReturn(spenderNum1.longValue())
				.willReturn(cryptoAllowance)
				.willReturn(tokenAllowanceVal)
				.willReturn(headTokenNum);
		given(in.readNormalisedString(anyInt())).willReturn(memo);
		given(in.readBoolean())
				.willReturn(deleted)
				.willReturn(smartContract)
				.willReturn(receiverSigRequired)
				.willReturn(approvedForAll);
		given(in.readInt())
				.willReturn(maxAutoAssociations)
				.willReturn(usedAutoAssociations)
				.willReturn(number)
				.willReturn(kvPairs)
				.willReturn(cryptoAllowances.size())
				.willReturn(fungibleTokenAllowances.size())
				.willReturn(approveForAllNfts.size())
				.willReturn(associatedTokensCount)
				.willReturn(numPositiveBalances);
		given(serdes.readNullableSerializable(in)).willReturn(proxy);
		given(in.readByteArray(Integer.MAX_VALUE)).willReturn(alias.toByteArray());
		given(in.readSerializable())
				.willReturn(tokenAllowanceKey1)
				.willReturn(tokenAllowanceKey2)
				.willReturn(tokenAllowanceValue);
		given(in.readLongList(Integer.MAX_VALUE))
				.willReturn(serialNumbers);

		newSubject.deserialize(in, MerkleAccountState.RELEASE_0250_VERSION);

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
		inOrder.verify(out).writeInt(maxAutoAssociations);
		inOrder.verify(out).writeInt(usedAutoAssociations);
		inOrder.verify(out).writeInt(number);
		inOrder.verify(out).writeByteArray(alias.toByteArray());

		inOrder.verify(out).writeInt(cryptoAllowances.size());
		inOrder.verify(out).writeLong(spenderNum1.longValue());
		inOrder.verify(out).writeLong(cryptoAllowance);

		inOrder.verify(out).writeInt(fungibleTokenAllowances.size());
		inOrder.verify(out).writeSerializable(tokenAllowanceKey1, true);
		inOrder.verify(out).writeLong(tokenAllowanceVal);

		inOrder.verify(out).writeInt(approveForAllNfts.size());
		inOrder.verify(out).writeSerializable(tokenAllowanceKey2, true);

		inOrder.verify(out).writeInt(associatedTokensCount);
		inOrder.verify(out).writeInt(numPositiveBalances);
		inOrder.verify(out).writeLong(headTokenNum);
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
				maxAutoAssociations,
				usedAutoAssociations,
				alias,
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				approveForAllNfts,
				associatedTokensCount,
				numPositiveBalances,
				headTokenNum);

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
				maxAutoAssociations,
				usedAutoAssociations,
				alias,
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				approveForAllNfts,
				associatedTokensCount,
				numPositiveBalances,
				headTokenNum);

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
				maxAutoAssociations,
				usedAutoAssociations,
				alias,
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				approveForAllNfts,
				associatedTokensCount,
				numPositiveBalances,
				headTokenNum);

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
				maxAutoAssociations,
				usedAutoAssociations,
				alias,
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				approveForAllNfts,
				associatedTokensCount,
				numPositiveBalances,
				headTokenNum);

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
				maxAutoAssociations,
				usedAutoAssociations,
				alias,
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				approveForAllNfts,
				associatedTokensCount,
				numPositiveBalances,
				headTokenNum);

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
				maxAutoAssociations,
				usedAutoAssociations,
				alias,
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				approveForAllNfts,
				associatedTokensCount,
				numPositiveBalances,
				headTokenNum);

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
				maxAutoAssociations,
				usedAutoAssociations,
				alias,
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				approveForAllNfts,
				associatedTokensCount,
				numPositiveBalances,
				headTokenNum);

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
				maxAutoAssociations,
				usedAutoAssociations,
				alias,
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				approveForAllNfts,
				associatedTokensCount,
				numPositiveBalances,
				headTokenNum);

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
				maxAutoAssociations,
				usedAutoAssociations,
				alias,
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				approveForAllNfts,
				associatedTokensCount,
				numPositiveBalances,
				headTokenNum);

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
				number,
				maxAutoAssociations,
				usedAutoAssociations,
				alias,
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				approveForAllNfts,
				associatedTokensCount,
				numPositiveBalances,
				headTokenNum);

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
				number,
				maxAutoAssociations,
				usedAutoAssociations,
				otherAlias,
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				approveForAllNfts,
				associatedTokensCount,
				numPositiveBalances,
				headTokenNum);

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
				number,
				maxAutoAssociations,
				usedAutoAssociations,
				alias,
				otherKvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				approveForAllNfts,
				associatedTokensCount,
				numPositiveBalances,
				headTokenNum);

		assertNotEquals(subject, otherSubject);
	}

	@Test
	void equalsWorksForAllowances1() {
		final EntityNum spenderNum1 = EntityNum.fromLong(100L);
		final Long cryptoAllowance = 100L;


		otherCryptoAllowances.put(spenderNum1, cryptoAllowance);

		final var otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				number,
				maxAutoAssociations,
				usedAutoAssociations,
				alias,
				kvPairs,
				otherCryptoAllowances,
				fungibleTokenAllowances,
				approveForAllNfts,
				associatedTokensCount,
				numPositiveBalances,
				headTokenNum);

		assertNotEquals(subject, otherSubject);
	}

	@Test
	void equalsWorksForAllowances2() {
		final EntityNum spenderNum1 = EntityNum.fromLong(100L);
		final EntityNum tokenForAllowance = EntityNum.fromLong(200L);
		final Long tokenAllowanceVal = 1L;

		final FcTokenAllowanceId tokenAllowanceKey = FcTokenAllowanceId.from(tokenForAllowance, spenderNum1);

		otherFungibleTokenAllowances.put(tokenAllowanceKey, tokenAllowanceVal);

		final var otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				number,
				maxAutoAssociations,
				usedAutoAssociations,
				alias,
				kvPairs,
				cryptoAllowances,
				otherFungibleTokenAllowances,
				approveForAllNfts,
				associatedTokensCount,
				numPositiveBalances,
				headTokenNum);

		assertNotEquals(subject, otherSubject);
	}

	@Test
	void equalsWorksForAllowances3() {
		final EntityNum spenderNum1 = EntityNum.fromLong(100L);
		final EntityNum tokenForAllowance = EntityNum.fromLong(200L);

		final FcTokenAllowanceId tokenAllowanceKey = FcTokenAllowanceId.from(tokenForAllowance, spenderNum1);


		otherApproveForAllNfts.add(tokenAllowanceKey);

		final var otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				number,
				maxAutoAssociations,
				usedAutoAssociations,
				alias,
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				otherApproveForAllNfts,
				associatedTokensCount,
				numPositiveBalances,
				headTokenNum);

		assertNotEquals(subject, otherSubject);
	}

	@Test
	void equalsWorksForTokenAssociationMetadata() {
		final var otherSubject = new MerkleAccountState(
				key,
				expiry, balance, autoRenewSecs,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy,
				number,
				maxAutoAssociations,
				usedAutoAssociations,
				alias,
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				approveForAllNfts,
				associatedTokensCount,
				numPositiveBalances+1,
				headTokenNum);

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
				maxAutoAssociations,
				usedAutoAssociations,
				alias,
				kvPairs,
				cryptoAllowances,
				fungibleTokenAllowances,
				approveForAllNfts,
				associatedTokensCount,
				numPositiveBalances,
				headTokenNum);

		final var otherSubject = new MerkleAccountState(
				otherKey,
				otherExpiry, otherBalance, otherAutoRenewSecs,
				otherMemo,
				otherDeleted, otherSmartContract, otherReceiverSigRequired,
				otherProxy,
				otherNumber,
				maxAutoAssociations,
				usedAutoAssociations,
				alias,
				otherKvPairs,
				otherCryptoAllowances,
				otherFungibleTokenAllowances,
				otherApproveForAllNfts,
				associatedTokensCount,
				numPositiveBalances,
				headTokenNum);

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
		defaultSubject.setUsedAutomaticAssociations(used);

		assertEquals(used, defaultSubject.getUsedAutomaticAssociations());
		assertEquals(max, defaultSubject.getMaxAutomaticAssociations());

		var toIncrement = defaultSubject.getUsedAutomaticAssociations();
		toIncrement++;

		defaultSubject.setUsedAutomaticAssociations(toIncrement);
		assertEquals(toIncrement, defaultSubject.getUsedAutomaticAssociations());

		var changeMax = max + 10;
		defaultSubject.setMaxAutomaticAssociations(changeMax);

		assertEquals(changeMax, defaultSubject.getMaxAutomaticAssociations());
	}

	private void setEmptyAllowances() {
		subject.setCryptoAllowances(new TreeMap<>());
		subject.setFungibleTokenAllowances(new TreeMap<>());
		subject.setApproveForAllNfts(new TreeSet<>());
	}

	@Test
	void gettersForAllowancesWork() {
		var subject = new MerkleAccountState();
		assertEquals(Collections.emptyMap(), subject.getCryptoAllowances());
		assertEquals(Collections.emptyMap(), subject.getFungibleTokenAllowances());
		assertEquals(Collections.emptySet(), subject.getApproveForAllNfts());
	}

	@Test
	void settersForAllowancesWork() {
		var subject = new MerkleAccountState();
		subject.setCryptoAllowances(cryptoAllowances);
		subject.setFungibleTokenAllowances(fungibleTokenAllowances);
		subject.setApproveForAllNfts(approveForAllNfts);
		assertEquals(cryptoAllowances, subject.getCryptoAllowances());
		assertEquals(fungibleTokenAllowances, subject.getFungibleTokenAllowances());
		assertEquals(approveForAllNfts, subject.getApproveForAllNfts());
	}

}
