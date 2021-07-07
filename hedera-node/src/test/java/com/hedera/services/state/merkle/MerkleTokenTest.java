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

import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.serdes.IoReadingFunction;
import com.hedera.services.state.serdes.IoWritingConsumer;
import com.hedera.services.state.submerkle.CustomFee;
import com.hedera.services.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.Fraction;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import proto.CustomFeesOuterClass;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static com.hedera.services.state.merkle.MerkleTopic.serdes;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

class MerkleTokenTest {
	private JKey adminKey, otherAdminKey;
	private JKey freezeKey, otherFreezeKey;
	private JKey wipeKey, otherWipeKey;
	private JKey supplyKey, otherSupplyKey;
	private JKey kycKey, otherKycKey;
	private String symbol = "NotAnHbar", otherSymbol = "NotAnHbarEither";
	private String name = "NotAnHbarName", otherName = "NotAnHbarNameEither";
	private String memo = "NotAMemo", otherMemo = "NotAMemoEither";
	private int decimals = 2, otherDecimals = 3;
	private long expiry = 1_234_567, otherExpiry = expiry + 2_345_678;
	private long autoRenewPeriod = 1_234_567, otherAutoRenewPeriod = 2_345_678;
	private long totalSupply = 1_000_000, otherTotalSupply = 1_000_001;
	private boolean freezeDefault = true, otherFreezeDefault = false;
	private boolean accountsKycGrantedByDefault = true, otherAccountsKycGrantedByDefault = false;
	private EntityId treasury = new EntityId(1, 2, 3);
	private EntityId otherTreasury = new EntityId(3, 2, 1);
	private EntityId autoRenewAccount = new EntityId(2, 3, 4);
	private EntityId otherAutoRenewAccount = new EntityId(4, 3, 2);
	private boolean isDeleted = true, otherIsDeleted = false;

	private final long validNumerator = 5;
	private final long validDenominator = 100;
	private final long fixedUnitsToCollect = 7;
	private final long minimumUnitsToCollect = 1;
	private final long maximumUnitsToCollect = 55;
	private final EntityId denom = new EntityId(1,2, 3);
	private final EntityId feeCollector = new EntityId(4,5, 6);
	final CustomFeesOuterClass.CustomFee fractionalFee = CustomFeesOuterClass.CustomFee.newBuilder()
			.setFeeCollectorAccountId(feeCollector.toGrpcAccountId())
			.setFractionalFee(CustomFeesOuterClass.FractionalFee.newBuilder()
					.setFractionalAmount(Fraction.newBuilder()
							.setNumerator(validNumerator)
							.setDenominator(validDenominator)
							.build())
					.setMinimumAmount(minimumUnitsToCollect)
					.setMaximumAmount(maximumUnitsToCollect)
			).build();
	final CustomFeesOuterClass.CustomFee fixedFee = CustomFeesOuterClass.CustomFee.newBuilder()
			.setFeeCollectorAccountId(feeCollector.toGrpcAccountId())
			.setFixedFee(CustomFeesOuterClass.FixedFee.newBuilder()
					.setDenominatingTokenId(denom.toGrpcTokenId())
					.setAmount(fixedUnitsToCollect)
			).build();
	final CustomFeesOuterClass.CustomFees grpcFeeSchedule = CustomFeesOuterClass.CustomFees.newBuilder()
			.addCustomFees(fixedFee)
			.addCustomFees(fractionalFee)
			.setCanUpdateWithAdminKey(true)
			.build();
	final List<CustomFee> feeSchedule = grpcFeeSchedule.getCustomFeesList().stream()
			.map(CustomFee::fromGrpc).collect(toList());

	private MerkleToken subject;
	private MerkleToken other;

	@BeforeEach
	void setup() {
		adminKey = new JEd25519Key("not-a-real-admin-key".getBytes());
		kycKey = new JEd25519Key("not-a-real-kyc-key".getBytes());
		freezeKey = new JEd25519Key("not-a-real-freeze-key".getBytes());
		otherAdminKey = new JEd25519Key("not-a-real-admin-key-either".getBytes());
		otherKycKey = new JEd25519Key("not-a-real-kyc-key-either".getBytes());
		otherFreezeKey = new JEd25519Key("not-a-real-freeze-key-either".getBytes());
		wipeKey = new JEd25519Key("not-a-real-wipe-key".getBytes());
		supplyKey = new JEd25519Key("not-a-real-supply-key".getBytes());
		otherWipeKey = new JEd25519Key("not-a-real-wipe-key-either".getBytes());
		otherSupplyKey = new JEd25519Key("not-a-real-supply-key-either".getBytes());

		subject = new MerkleToken(
				expiry, totalSupply, decimals, symbol, name, freezeDefault, accountsKycGrantedByDefault, treasury);
		setOptionalElements(subject);
		subject.setExpiry(expiry);
		subject.setTotalSupply(totalSupply);
		subject.setAdminKey(adminKey);
		subject.setFreezeKey(freezeKey);
		subject.setKycKey(kycKey);
		subject.setWipeKey(wipeKey);
		subject.setSupplyKey(supplyKey);
		subject.setDeleted(isDeleted);
		subject.setMemo(memo);
		subject.setTokenType(TokenType.FUNGIBLE_COMMON);
		subject.setSupplyType(TokenSupplyType.INFINITE);
		subject.setTreasury(treasury);
		subject.setName(name);
		subject.setSymbol(symbol);
		subject.setAccountsFrozenByDefault(true);
		subject.setFeeScheduleFrom(grpcFeeSchedule);

		serdes = mock(DomainSerdes.class);
		MerkleToken.serdes = serdes;
	}

	@AfterEach
	void cleanup() {
		MerkleToken.serdes = new DomainSerdes();
	}

	@Test
	void deleteIsNoop() {
		// expect:
		assertDoesNotThrow(subject::release);
	}

	@Test
	void serializeWorks() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		// and:
		InOrder inOrder = inOrder(serdes, out);

		// when:
		subject.serialize(out);

		// then:
		inOrder.verify(out).writeBoolean(isDeleted);
		inOrder.verify(out).writeLong(expiry);
		inOrder.verify(serdes).writeNullableSerializable(autoRenewAccount, out);
		inOrder.verify(out).writeLong(autoRenewPeriod);
		inOrder.verify(out).writeNormalisedString(symbol);
		inOrder.verify(out).writeNormalisedString(name);
		inOrder.verify(out).writeSerializable(treasury, true);
		inOrder.verify(out).writeLong(totalSupply);
		inOrder.verify(out).writeInt(decimals);
		inOrder.verify(out, times(2)).writeBoolean(true);
		inOrder.verify(serdes).writeNullable(
				argThat(adminKey::equals), argThat(out::equals), any(IoWritingConsumer.class));
		inOrder.verify(serdes).writeNullable(
				argThat(freezeKey::equals), argThat(out::equals), any(IoWritingConsumer.class));
		inOrder.verify(serdes).writeNullable(
				argThat(kycKey::equals), argThat(out::equals), any(IoWritingConsumer.class));
		inOrder.verify(serdes).writeNullable(
				argThat(supplyKey::equals), argThat(out::equals), any(IoWritingConsumer.class));
		inOrder.verify(serdes).writeNullable(
				argThat(wipeKey::equals), argThat(out::equals), any(IoWritingConsumer.class));
		inOrder.verify(out).writeNormalisedString(memo);
		inOrder.verify(out, times(2)).writeInt(0);
		inOrder.verify(out, times(2)).writeLong(0);
		inOrder.verify(out).writeSerializableList(feeSchedule, true, true);
		inOrder.verify(out).writeBoolean(subject.isFeeScheduleMutable());
	}

	@Test
	void copyWorks() {
		// given:
		var copySubject = subject.copy();

		// expect:
		assertNotSame(copySubject, subject);
		assertEquals(subject, copySubject);
	}

	@Test
	void getterWorks() {
		// expect:
		assertEquals(feeSchedule, subject.customFeeSchedule());
	}

	@Test
	void v0120DeserializeWorks() throws IOException {
		// setup:
		SerializableDataInputStream fin = mock(SerializableDataInputStream.class);
		subject.setFeeScheduleFrom(CustomFeesOuterClass.CustomFees.getDefaultInstance());

		given(serdes.readNullableSerializable(any())).willReturn(autoRenewAccount);
		given(serdes.deserializeKey(fin)).willReturn(adminKey);
		given(serdes.readNullable(argThat(fin::equals), any(IoReadingFunction.class)))
				.willReturn(adminKey)
				.willReturn(freezeKey)
				.willReturn(kycKey)
				.willReturn(supplyKey)
				.willReturn(wipeKey);
		given(fin.readNormalisedString(anyInt()))
				.willReturn(symbol)
				.willReturn(name)
				.willReturn(memo);
		given(fin.readLong())
				.willReturn(subject.expiry())
				.willReturn(subject.autoRenewPeriod())
				.willReturn(subject.totalSupply())
				.willReturn(subject.getLastUsedSerialNumber());
		given(fin.readInt()).willReturn(subject.decimals())
				.willReturn(TokenType.FUNGIBLE_COMMON.ordinal())
				.willReturn(TokenSupplyType.INFINITE.ordinal());
		given(fin.readBoolean())
				.willReturn(isDeleted)
				.willReturn(subject.accountsAreFrozenByDefault());
		given(fin.readSerializable()).willReturn(subject.treasury());
		// and:
		var read = new MerkleToken();

		// when:
		read.deserialize(fin, MerkleToken.RELEASE_0120_VERSION);

		// then:
		assertEquals(subject, read);
	}

	@Test
	void v0160DeserializeWorks() throws IOException {
		// setup:
		SerializableDataInputStream fin = mock(SerializableDataInputStream.class);

		given(serdes.readNullableSerializable(any())).willReturn(autoRenewAccount);
		given(serdes.deserializeKey(fin)).willReturn(adminKey);
		given(serdes.readNullable(argThat(fin::equals), any(IoReadingFunction.class)))
				.willReturn(adminKey)
				.willReturn(freezeKey)
				.willReturn(kycKey)
				.willReturn(supplyKey)
				.willReturn(wipeKey);
		given(fin.readNormalisedString(anyInt()))
				.willReturn(symbol)
				.willReturn(name)
				.willReturn(memo);
		given(fin.readLong())
				.willReturn(subject.expiry())
				.willReturn(subject.autoRenewPeriod())
				.willReturn(subject.totalSupply())
				.willReturn(subject.maxSupply())
				.willReturn(subject.getLastUsedSerialNumber());
		given(fin.readInt())
				.willReturn(subject.decimals())
				.willReturn(subject.tokenType().ordinal())
				.willReturn(subject.supplyType().ordinal());
		given(fin.readBoolean())
				.willReturn(isDeleted)
				.willReturn(subject.accountsAreFrozenByDefault())
				.willReturn(subject.isFeeScheduleMutable());
		given(fin.readSerializable()).willReturn(subject.treasury());
		given(fin.<CustomFee>readSerializableList(eq(Integer.MAX_VALUE), eq(true), any()))
				.willReturn(feeSchedule);
		// and:
		var read = new MerkleToken();

		// when:
		read.deserialize(fin, MerkleToken.MERKLE_VERSION);

		// then:
		assertEquals(subject, read);
	}

	@Test
	void objectContractHoldsForDifferentMemos() {
		// given:
		other = new MerkleToken(
				expiry, totalSupply, decimals, symbol, name, freezeDefault, accountsKycGrantedByDefault, treasury);
		setOptionalElements(other);
		other.setMemo(otherMemo);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void objectContractHoldsForDifferentTotalSupplies() {
		// given:
		other = new MerkleToken(
				expiry, otherTotalSupply, decimals, symbol, name, freezeDefault, accountsKycGrantedByDefault, treasury);
		setOptionalElements(other);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void objectContractHoldsForDifferentDecimals() {
		// given:
		other = new MerkleToken(
				expiry, totalSupply, otherDecimals, symbol, name, freezeDefault, accountsKycGrantedByDefault, treasury);
		setOptionalElements(other);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void objectContractHoldsForDifferentWipeKey() {
		// given:
		other = new MerkleToken(
				expiry, totalSupply, decimals, symbol, name, freezeDefault, accountsKycGrantedByDefault, treasury);
		setOptionalElements(other);
		other.setWipeKey(otherWipeKey);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void objectContractHoldsForDifferentSupply() {
		// given:
		other = new MerkleToken(
				expiry, totalSupply, decimals, symbol, name, freezeDefault, accountsKycGrantedByDefault, treasury);
		setOptionalElements(other);
		other.setSupplyKey(otherSupplyKey);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void objectContractHoldsForDifferentDeleted() {
		// given:
		other = new MerkleToken(
				expiry, totalSupply, decimals, symbol, name, freezeDefault, accountsKycGrantedByDefault, treasury);
		setOptionalElements(other);
		other.setDeleted(otherIsDeleted);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void objectContractHoldsForDifferentAdminKey() {
		// given:
		other = new MerkleToken(
				expiry, totalSupply, decimals, symbol, name, freezeDefault, accountsKycGrantedByDefault, treasury);
		setOptionalElements(other);
		other.setAdminKey(otherAdminKey);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void objectContractHoldsForDifferentAutoRenewPeriods() {
		// given:
		other = new MerkleToken(
				expiry, totalSupply, decimals, symbol, name, freezeDefault, accountsKycGrantedByDefault, treasury);
		setOptionalElements(other);
		other.setAutoRenewPeriod(otherAutoRenewPeriod);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void objectContractHoldsForDifferentAutoRenewAccounts() {
		// given:
		other = new MerkleToken(
				expiry, totalSupply, decimals, symbol, name, freezeDefault, accountsKycGrantedByDefault, treasury);
		setOptionalElements(other);
		other.setAutoRenewAccount(otherAutoRenewAccount);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void objectContractHoldsForDifferentExpiries() {
		// given:
		other = new MerkleToken(
				otherExpiry, totalSupply, decimals, symbol, name, freezeDefault, accountsKycGrantedByDefault, treasury);
		setOptionalElements(other);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void objectContractHoldsForDifferentSymbol() {
		// given:
		other = new MerkleToken(
				expiry, totalSupply, decimals, otherSymbol, name, freezeDefault, accountsKycGrantedByDefault, treasury);
		setOptionalElements(other);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void objectContractHoldsForDifferentName() {
		// given:
		other = new MerkleToken(
				expiry, totalSupply, decimals, symbol, otherName, freezeDefault, accountsKycGrantedByDefault, treasury);
		setOptionalElements(other);
		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void objectContractHoldsForDifferentFreezeDefault() {
		// given:
		other = new MerkleToken(
				expiry, totalSupply, decimals, symbol, name, otherFreezeDefault, accountsKycGrantedByDefault, treasury);
		setOptionalElements(other);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void objectContractHoldsForDifferentaccountsKycGrantedByDefault() {
		// given:
		other = new MerkleToken(
				expiry, totalSupply, decimals, symbol, name, freezeDefault, otherAccountsKycGrantedByDefault, treasury);
		setOptionalElements(other);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void objectContractHoldsForDifferentTreasury() {
		// given:
		other = new MerkleToken(
				expiry, totalSupply, decimals, symbol, name, freezeDefault, accountsKycGrantedByDefault, otherTreasury);
		setOptionalElements(other);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void objectContractHoldsForDifferentKycKeys() {
		// given:
		other = new MerkleToken(
				expiry, totalSupply, decimals, symbol, name, freezeDefault, accountsKycGrantedByDefault, treasury);
		setOptionalElements(other);
		other.setKycKey(otherKycKey);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());

		// and given:
		other.setKycKey(MerkleToken.UNUSED_KEY);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	void objectContractHoldsForDifferentFreezeKeys() {
		// given:
		other = new MerkleToken(
				expiry, totalSupply, decimals, symbol, name, freezeDefault, accountsKycGrantedByDefault, treasury);
		setOptionalElements(other);
		other.setFreezeKey(otherFreezeKey);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());

		// and given:
		other.setFreezeKey(MerkleToken.UNUSED_KEY);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	 void objectContractPropertiesCheck() {
		assertTrue(subject.hasAdminKey());
		assertEquals(adminKey, subject.adminKey().get());
		assertEquals(freezeKey, subject.freezeKey().get());
		assertTrue(subject.hasFreezeKey());
		assertEquals(kycKey, subject.kycKey().get());
		assertTrue(subject.hasKycKey());
		assertEquals(supplyKey, subject.supplyKey().get());
		assertTrue(subject.hasSupplyKey());
		assertEquals(wipeKey, subject.wipeKey().get());
		assertTrue(subject.hasWipeKey());
		assertTrue(subject.isDeleted());
		assertEquals(symbol, subject.symbol());
		assertEquals(name, subject.name());
		assertTrue(subject.accountsKycGrantedByDefault());
		assertEquals(autoRenewAccount, subject.autoRenewAccount());
		assertTrue(subject.hasAutoRenewAccount());
		assertEquals(supplyKey, subject.getSupplyKey());
		assertEquals(wipeKey, subject.getWipeKey());
		assertEquals(kycKey, subject.getKycKey());
		assertEquals(freezeKey, subject.getFreezeKey());
		assertEquals(memo, subject.memo());
	}

	private void setOptionalElements(MerkleToken token) {
		token.setDeleted(isDeleted);
		token.setAdminKey(adminKey);
		token.setFreezeKey(freezeKey);
		token.setWipeKey(wipeKey);
		token.setSupplyKey(supplyKey);
		token.setKycKey(kycKey);
		token.setAutoRenewAccount(autoRenewAccount);
		token.setAutoRenewPeriod(autoRenewPeriod);
		token.setMemo(memo);
		token.setFeeScheduleFrom(grpcFeeSchedule);
	}

	@Test
	void hashCodeContractMet() {
		// given:
		var defaultSubject = new MerkleAccountState();
		// and:
		var identicalSubject = new MerkleToken(
				expiry, totalSupply, decimals, symbol, name, freezeDefault, accountsKycGrantedByDefault, treasury);
		setOptionalElements(identicalSubject);
		identicalSubject.setDeleted(isDeleted);
		identicalSubject.setTokenType(TokenType.FUNGIBLE_COMMON);
		identicalSubject.setSupplyType(TokenSupplyType.INFINITE);
		identicalSubject.setMaxSupply(subject.maxSupply());
		identicalSubject.setLastUsedSerialNumber(subject.getLastUsedSerialNumber());

		// and:
		other = new MerkleToken(
				otherExpiry, otherTotalSupply, otherDecimals, otherSymbol, otherName,
				otherFreezeDefault, otherAccountsKycGrantedByDefault, otherTreasury);
		other.setTokenType(TokenType.FUNGIBLE_COMMON);
		other.setSupplyType(TokenSupplyType.INFINITE);
		other.setMaxSupply(subject.maxSupply());
		other.setLastUsedSerialNumber(subject.getLastUsedSerialNumber());
		// expect:
		assertNotEquals(subject.hashCode(), defaultSubject.hashCode());
		assertNotEquals(subject.hashCode(), other.hashCode());
		assertEquals(subject.hashCode(), identicalSubject.hashCode());
	}

	@Test
	void equalsWorksWithExtremes() {
		// expect:
		assertEquals(subject, subject);
		assertNotEquals(subject, null);
		assertNotEquals(subject, new Object());
	}

	@Test
	void toStringWorks() {
		// setup:
		final var desired = "MerkleToken{tokenType=FUNGIBLE_COMMON, supplyType=INFINITE, deleted=true, expiry=1234567, " +
				"symbol=NotAnHbar, name=NotAnHbarName, memo=NotAMemo, treasury=1.2.3, maxSupply=0, totalSupply=1000000," +
				" decimals=2, lastUsedSerialNumber=0, autoRenewAccount=2.3.4, autoRenewPeriod=1234567, " +
				"adminKey=ed25519: \"not-a-real-admin-key\"\n" +
				", kycKey=ed25519: \"not-a-real-kyc-key\"\n" +
				", wipeKey=ed25519: \"not-a-real-wipe-key\"\n" +
				", supplyKey=ed25519: \"not-a-real-supply-key\"\n" +
				", freezeKey=ed25519: \"not-a-real-freeze-key\"\n" +
				", accountsKycGrantedByDefault=true, accountsFrozenByDefault=true, " +
				"feeSchedules=[CustomFee{feeType=FIXED_FEE, fixedFee=FixedFeeSpec{unitsToCollect=7, tokenDenomination=1" +
				".2.3}, feeCollector=EntityId{shard=4, realm=5, num=6}}, CustomFee{feeType=FRACTIONAL_FEE, " +
				"fractionalFee=FractionalFeeSpec{numerator=5, denominator=100, minimumUnitsToCollect=1, " +
				"maximumUnitsToCollect=55}, feeCollector=EntityId{shard=4, realm=5, num=6}}], feeScheduleMutable=true}";

		// expect:
		assertEquals(desired, subject.toString());
	}

	@Test
	void merkleMethodsWork() {
		// expect;
		assertEquals(MerkleToken.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleToken.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertTrue(subject.isLeaf());
	}

	@Test
	void adjustsTotalSupplyWhenValid() {
		// when:
		subject.adjustTotalSupplyBy(500_000);

		// then:
		assertEquals(1_500_000, subject.totalSupply());
	}

	@Test
	void doesNotAdjustTotalSupplyWhenInvalid() {
		// when:
		subject.setMaxSupply(2);

		// then:
		assertThrows(IllegalArgumentException.class, () -> subject.adjustTotalSupplyBy(1_500_000));
	}

	@Test
	void throwsIaeIfTotalSupplyGoesNegative() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.adjustTotalSupplyBy(-1_500_000));
	}

	@Test
	void liveFireSerdeWorks() throws IOException, ConstructableRegistryException {
		// setup:
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final var dos = new SerializableDataOutputStream(baos);
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(CustomFee.class, CustomFee::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(EntityId.class, EntityId::new));
		MerkleToken.serdes = new DomainSerdes();

		// given:
		subject.serialize(dos);
		dos.flush();
		// and:
		final var bytes = baos.toByteArray();
		final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		final var din = new SerializableDataInputStream(bais);

		// when:
		final var newSubject = new MerkleToken();
		newSubject.deserialize(din, MerkleToken.MERKLE_VERSION);

		// then:
		assertEquals(subject, newSubject);
	}

	@Test
	void returnCorrectGrpcFeeSchedule() {
		final var token = new MerkleToken(
				expiry, totalSupply, decimals, symbol, name, freezeDefault, accountsKycGrantedByDefault, treasury);
		assertEquals(CustomFeesOuterClass.CustomFees.getDefaultInstance(), token.grpcFeeSchedule());

		token.setFeeScheduleFrom(grpcFeeSchedule);
		assertEquals(grpcFeeSchedule, token.grpcFeeSchedule());
	}
}
