package com.hedera.services.state.submerkle;

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

import com.hedera.test.factories.fees.CustomFeeBuilder;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static com.hedera.test.factories.fees.CustomFeeBuilder.fixedHbar;
import static com.hedera.test.factories.fees.CustomFeeBuilder.fixedHts;
import static com.hedera.test.factories.fees.CustomFeeBuilder.fractional;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FcCustomFeeTest {
	private final long validNumerator = 5;
	private final long validDenominator = 100;
	private final long invalidDenominator = 0;
	private final long fixedUnitsToCollect = 7;
	private final long minimumUnitsToCollect = 1;
	private final long maximumUnitsToCollect = 55;
	private final EntityId denom = new EntityId(1, 2, 3);
	private final TokenID grpcDenom = denom.toGrpcTokenId();
	private final EntityId feeCollector = new EntityId(4, 5, 6);
	private final AccountID grpcFeeCollector = feeCollector.toGrpcAccountId();
	private final CustomFeeBuilder builder = new CustomFeeBuilder(grpcFeeCollector);

	@Mock
	private SerializableDataInputStream din;
	@Mock
	private SerializableDataOutputStream dos;

	@Test
	void grpcConversionWorksForFixed() {
		final var targetId = new EntityId(7, 8, 9);
		final var expectedHtsSubject = FcCustomFee.fixedFee(fixedUnitsToCollect, denom, feeCollector);
		final var expectedHtsSameTokenSubject = FcCustomFee.fixedFee(fixedUnitsToCollect, targetId, feeCollector);
		final var expectedHbarSubject = FcCustomFee.fixedFee(fixedUnitsToCollect, null, feeCollector);
		final var htsGrpc = builder.withFixedFee(fixedHts(grpcDenom, fixedUnitsToCollect));
		final var htsSameTokenGrpc = builder.withFixedFee(fixedHts(fixedUnitsToCollect));
		final var hbarGrpc = builder.withFixedFee(fixedHbar(fixedUnitsToCollect));

		final var htsSubject = FcCustomFee.fromGrpc(htsGrpc, null);
		final var htsSameTokenSubject = FcCustomFee.fromGrpc(htsSameTokenGrpc, targetId);
		final var hbarSubject = FcCustomFee.fromGrpc(hbarGrpc, null);

		assertEquals(expectedHtsSubject, htsSubject);
		assertEquals(expectedHtsSameTokenSubject, htsSameTokenSubject);
		assertEquals(expectedHbarSubject, hbarSubject);
	}

	@Test
	void grpcReprWorksForFixedHbar() {
		final var expected = builder.withFixedFee(fixedHbar(fixedUnitsToCollect));
		final var hbarFee = FcCustomFee.fixedFee(fixedUnitsToCollect, null, feeCollector);

		final var actual = hbarFee.asGrpc();

		assertEquals(expected, actual);
	}

	@Test
	void grpcReprWorksForFixedHts() {
		final var expected = builder.withFixedFee(fixedHts(grpcDenom, fixedUnitsToCollect));
		final var htsFee = FcCustomFee.fixedFee(fixedUnitsToCollect, EntityId.fromGrpcTokenId(grpcDenom), feeCollector);

		final var actual = htsFee.asGrpc();

		assertEquals(expected, actual);
	}

	@Test
	void grpcReprWorksForFractional() {
		final var expected = builder.withFractionalFee(
				fractional(validNumerator, validDenominator)
						.setMinimumAmount(minimumUnitsToCollect)
						.setMaximumAmount(maximumUnitsToCollect));
		final var fractionalFee = FcCustomFee.fractionalFee(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect,
				feeCollector);

		final var actual = fractionalFee.asGrpc();

		assertEquals(expected, actual);
	}

	@Test
	void grpcReprWorksForFractionalNoMax() {
		final var expected = builder.withFractionalFee(
				fractional(validNumerator, validDenominator)
						.setMinimumAmount(minimumUnitsToCollect));
		final var fractionalFee = FcCustomFee.fractionalFee(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				Long.MAX_VALUE,
				feeCollector);

		final var actual = fractionalFee.asGrpc();

		assertEquals(expected, actual);
	}

	@Test
	void grpcConversionWorksForFractional() {
		final var expectedExplicitMaxSubject = FcCustomFee.fractionalFee(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect,
				feeCollector);
		final var expectedNoExplicitMaxSubject = FcCustomFee.fractionalFee(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				Long.MAX_VALUE,
				feeCollector);
		final var grpcWithExplicitMax = builder.withFractionalFee(
				fractional(validNumerator, validDenominator)
						.setMinimumAmount(minimumUnitsToCollect)
						.setMaximumAmount(maximumUnitsToCollect));
		final var grpcWithoutExplicitMax = builder.withFractionalFee(
				fractional(validNumerator, validDenominator)
						.setMinimumAmount(minimumUnitsToCollect));

		final var explicitMaxSubject = FcCustomFee.fromGrpc(grpcWithExplicitMax, null);
		final var noExplicitMaxSubject = FcCustomFee.fromGrpc(grpcWithoutExplicitMax, null);

		assertEquals(expectedExplicitMaxSubject, explicitMaxSubject);
		assertEquals(expectedNoExplicitMaxSubject, noExplicitMaxSubject);
	}

	@Test
	void liveFireSerdesWorkForFractional() throws IOException {
		final var subject = FcCustomFee.fractionalFee(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect,
				feeCollector);
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final var dos = new SerializableDataOutputStream(baos);
		subject.serialize(dos);
		dos.flush();
		final var bytes = baos.toByteArray();
		final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		final var din = new SerializableDataInputStream(bais);

		final var newSubject = new FcCustomFee();
		newSubject.deserialize(din, FcCustomFee.MERKLE_VERSION);

		assertEquals(subject.getFractionalFeeSpec(), newSubject.getFractionalFeeSpec());
		assertEquals(subject.getFeeCollectorAccountId(), newSubject.getFeeCollectorAccountId());
	}

	@Test
	void liveFireSerdesWorkForFixed() throws IOException {
		final var fixedSubject = FcCustomFee.fixedFee(fixedUnitsToCollect, denom, feeCollector);
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final var dos = new SerializableDataOutputStream(baos);
		fixedSubject.serialize(dos);
		dos.flush();
		final var bytes = baos.toByteArray();
		final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		final var din = new SerializableDataInputStream(bais);

		final var newSubject = new FcCustomFee();
		newSubject.deserialize(din, FcCustomFee.MERKLE_VERSION);

		assertEquals(fixedSubject.getFixedFeeSpec(), newSubject.getFixedFeeSpec());
		assertEquals(fixedSubject.getFeeCollectorAccountId(), newSubject.getFeeCollectorAccountId());
	}

	@Test
	void liveFireSerdesWorkForFixedWithNullDenom() throws IOException {
		final var fixedSubject = FcCustomFee.fixedFee(fixedUnitsToCollect, null, feeCollector);
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final var dos = new SerializableDataOutputStream(baos);
		fixedSubject.serialize(dos);
		dos.flush();
		final var bytes = baos.toByteArray();
		final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		final var din = new SerializableDataInputStream(bais);

		final var newSubject = new FcCustomFee();
		newSubject.deserialize(din, FcCustomFee.MERKLE_VERSION);

		assertEquals(fixedSubject.getFixedFeeSpec(), newSubject.getFixedFeeSpec());
		assertEquals(fixedSubject.getFeeCollectorAccountId(), newSubject.getFeeCollectorAccountId());
	}

	@Test
	void deserializeWorksAsExpectedForFixed() throws IOException {
		final var expectedFixedSpec = new FcCustomFee.FixedFeeSpec(fixedUnitsToCollect, denom);
		given(din.readByte()).willReturn(FcCustomFee.FIXED_CODE);
		given(din.readLong()).willReturn(fixedUnitsToCollect);
		given(din.readSerializable(anyBoolean(), Mockito.any())).willReturn(denom).willReturn(feeCollector);

		final var subject = new FcCustomFee();
		subject.deserialize(din, FcCustomFee.MERKLE_VERSION);

		assertEquals(FcCustomFee.FeeType.FIXED_FEE, subject.getFeeType());
		assertEquals(expectedFixedSpec, subject.getFixedFeeSpec());
		assertNull(subject.getFractionalFeeSpec());
		assertEquals(feeCollector, subject.getFeeCollectorAccountId());
	}

	@Test
	void deserializeWorksAsExpectedForFractional() throws IOException {
		final var expectedFractionalSpec = new FcCustomFee.FractionalFeeSpec(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect);
		given(din.readByte()).willReturn(FcCustomFee.FRACTIONAL_CODE);
		given(din.readLong())
				.willReturn(validNumerator)
				.willReturn(validDenominator)
				.willReturn(minimumUnitsToCollect)
				.willReturn(maximumUnitsToCollect);
		given(din.readSerializable(anyBoolean(), Mockito.any())).willReturn(feeCollector);

		final var subject = new FcCustomFee();
		subject.deserialize(din, FcCustomFee.MERKLE_VERSION);

		assertEquals(FcCustomFee.FeeType.FRACTIONAL_FEE, subject.getFeeType());
		assertEquals(expectedFractionalSpec, subject.getFractionalFeeSpec());
		assertNull(subject.getFixedFeeSpec());
		assertEquals(feeCollector, subject.getFeeCollectorAccountId());
	}

	@Test
	void serializeWorksAsExpectedForFractional() throws IOException {
		InOrder inOrder = Mockito.inOrder(dos);
		final var subject = FcCustomFee.fractionalFee(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect,
				feeCollector);

		subject.serialize(dos);

		inOrder.verify(dos).writeByte(FcCustomFee.FRACTIONAL_CODE);
		inOrder.verify(dos).writeLong(validNumerator);
		inOrder.verify(dos).writeLong(validDenominator);
		inOrder.verify(dos).writeLong(minimumUnitsToCollect);
		inOrder.verify(dos).writeLong(maximumUnitsToCollect);
		inOrder.verify(dos).writeSerializable(feeCollector, true);
	}

	@Test
	void serializeWorksAsExpectedForFixed() throws IOException {
		InOrder inOrder = Mockito.inOrder(dos);
		final var subject = FcCustomFee.fixedFee(fixedUnitsToCollect, denom, feeCollector);

		subject.serialize(dos);

		inOrder.verify(dos).writeByte(FcCustomFee.FIXED_CODE);
		inOrder.verify(dos).writeLong(fixedUnitsToCollect);
		inOrder.verify(dos).writeSerializable(denom, true);
		inOrder.verify(dos).writeSerializable(feeCollector, true);
	}


	@Test
	void merkleMethodsWork() {
		final var subject = FcCustomFee.fixedFee(fixedUnitsToCollect, denom, feeCollector);

		assertEquals(FcCustomFee.MERKLE_VERSION, subject.getVersion());
		assertEquals(FcCustomFee.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	@Test
	void fixedFactoryWorks() {
		final var expectedFixedSpec = new FcCustomFee.FixedFeeSpec(fixedUnitsToCollect, denom);
		final var fixedSubject = FcCustomFee.fixedFee(fixedUnitsToCollect, denom, feeCollector);

		assertEquals(FcCustomFee.FeeType.FIXED_FEE, fixedSubject.getFeeType());
		assertEquals(expectedFixedSpec, fixedSubject.getFixedFeeSpec());
		assertNull(fixedSubject.getFractionalFeeSpec());
		assertEquals(feeCollector, fixedSubject.getFeeCollectorAccountId());
	}

	@Test
	void fractionalFactoryWorks() {
		final var expectedFractionalSpec = new FcCustomFee.FractionalFeeSpec(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect);
		final var fractionalSubject = FcCustomFee.fractionalFee(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect,
				feeCollector);

		assertEquals(FcCustomFee.FeeType.FRACTIONAL_FEE, fractionalSubject.getFeeType());
		assertEquals(expectedFractionalSpec, fractionalSubject.getFractionalFeeSpec());
		assertNull(fractionalSubject.getFixedFeeSpec());
		assertEquals(feeCollector, fractionalSubject.getFeeCollectorAccountId());
	}

	@Test
	void toStringsWork() {
		final var fractionalSpec = new FcCustomFee.FractionalFeeSpec(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect);
		final var fixedSpec = new FcCustomFee.FixedFeeSpec(fixedUnitsToCollect, denom);
		final var desiredFracRepr = "FractionalFeeSpec{numerator=5, denominator=100, " +
				"minimumUnitsToCollect=1, maximumUnitsToCollect=55}";
		final var desiredFixedRepr = "FixedFeeSpec{unitsToCollect=7, tokenDenomination=1.2.3}";

		assertEquals(desiredFixedRepr, fixedSpec.toString());
		assertEquals(desiredFracRepr, fractionalSpec.toString());
	}

	@Test
	void failsFastIfNonPositiveFeeUsed() {
		assertThrows(IllegalArgumentException.class, () -> new FcCustomFee.FixedFeeSpec(0, denom));
		assertThrows(IllegalArgumentException.class, () -> new FcCustomFee.FixedFeeSpec(-1, denom));
	}

	@Test
	void failFastIfInvalidFractionUsed() {
		assertThrows(IllegalArgumentException.class, () -> new FcCustomFee.FractionalFeeSpec(
				validNumerator,
				invalidDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect));
		assertThrows(IllegalArgumentException.class, () -> new FcCustomFee.FractionalFeeSpec(
				-validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect));
		assertThrows(IllegalArgumentException.class, () -> new FcCustomFee.FractionalFeeSpec(
				validNumerator,
				-validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect));
		assertThrows(IllegalArgumentException.class, () -> new FcCustomFee.FractionalFeeSpec(
				validNumerator,
				validDenominator,
				-minimumUnitsToCollect,
				maximumUnitsToCollect));
		assertThrows(IllegalArgumentException.class, () -> new FcCustomFee.FractionalFeeSpec(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				-maximumUnitsToCollect));
		assertThrows(IllegalArgumentException.class, () -> new FcCustomFee.FractionalFeeSpec(
				validNumerator,
				validDenominator,
				maximumUnitsToCollect,
				minimumUnitsToCollect));
	}

	@Test
	void gettersWork() {
		final var fractionalSpec = new FcCustomFee.FractionalFeeSpec(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect);
		final var fixedSpec = new FcCustomFee.FixedFeeSpec(fixedUnitsToCollect, denom);

		assertEquals(validNumerator, fractionalSpec.getNumerator());
		assertEquals(validDenominator, fractionalSpec.getDenominator());
		assertEquals(minimumUnitsToCollect, fractionalSpec.getMinimumAmount());
		assertEquals(maximumUnitsToCollect, fractionalSpec.getMaximumUnitsToCollect());
		assertEquals(fixedUnitsToCollect, fixedSpec.getUnitsToCollect());
		assertEquals(denom, fixedSpec.getTokenDenomination());
	}

	@Test
	void hashCodeWorks() {
		final var fractionalSpec = new FcCustomFee.FractionalFeeSpec(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect);
		final var fixedSpec = new FcCustomFee.FixedFeeSpec(fixedUnitsToCollect, denom);

		assertDoesNotThrow(fractionalSpec::hashCode);
		assertDoesNotThrow(fixedSpec::hashCode);
	}

	@Test
	void fixedFeeEqualsWorks() {
		final var aFixedSpec = new FcCustomFee.FixedFeeSpec(fixedUnitsToCollect, denom);
		final var bFixedSpec = new FcCustomFee.FixedFeeSpec(fixedUnitsToCollect, denom);
		final var cFixedSpec = new FcCustomFee.FixedFeeSpec(fixedUnitsToCollect + 1, denom);
		final var dFixedSpec = new FcCustomFee.FixedFeeSpec(fixedUnitsToCollect, null);
		final var eFixedSpec = aFixedSpec;

		assertEquals(aFixedSpec, bFixedSpec);
		assertEquals(aFixedSpec, eFixedSpec);
		assertNotEquals(null, aFixedSpec);
		assertNotEquals(aFixedSpec, new Object());
		assertNotEquals(aFixedSpec, cFixedSpec);
		assertNotEquals(aFixedSpec, dFixedSpec);
	}

	@Test
	void fractionalFeeEqualsWorks() {
		long n = 3;
		long d = 7;
		long min = 22;
		long max = 99;
		final var aFractionalSpec = new FcCustomFee.FractionalFeeSpec(n, d, min, max);
		final var bFractionalSpec = new FcCustomFee.FractionalFeeSpec(n + 1, d, min, max);
		final var cFractionalSpec = new FcCustomFee.FractionalFeeSpec(n, d + 1, min, max);
		final var dFractionalSpec = new FcCustomFee.FractionalFeeSpec(n, d, min + 1, max);
		final var eFractionalSpec = new FcCustomFee.FractionalFeeSpec(n, d, min, max + 1);
		final var fFractionalSpec = new FcCustomFee.FractionalFeeSpec(n, d, min, max);
		final var gFractionalSpec = aFractionalSpec;

		assertEquals(aFractionalSpec, fFractionalSpec);
		assertEquals(aFractionalSpec, gFractionalSpec);
		assertNotEquals(null, aFractionalSpec);
		assertNotEquals(aFractionalSpec, new Object());
		assertNotEquals(aFractionalSpec, bFractionalSpec);
		assertNotEquals(aFractionalSpec, cFractionalSpec);
		assertNotEquals(aFractionalSpec, dFractionalSpec);
		assertNotEquals(aFractionalSpec, eFractionalSpec);
	}

	@Test
	void customFeeEqualsWorks() {
		long n = 3;
		long d = 7;
		long min = 22;
		long max = 99;
		final var aFeeCollector = new EntityId(1, 2, 3);
		final var bFeeCollector = new EntityId(2, 3, 4);
		final var aCustomFee = FcCustomFee.fixedFee(fixedUnitsToCollect, denom, aFeeCollector);
		final var bCustomFee = FcCustomFee.fixedFee(fixedUnitsToCollect + 1, denom, aFeeCollector);
		final var cCustomFee = FcCustomFee.fixedFee(fixedUnitsToCollect, denom, bFeeCollector);
		final var dCustomFee = FcCustomFee.fractionalFee(n, d, min, max, aFeeCollector);
		final var eCustomFee = aCustomFee;
		final var fCustomFee = FcCustomFee.fixedFee(fixedUnitsToCollect, denom, aFeeCollector);

		assertEquals(aCustomFee, eCustomFee);
		assertEquals(aCustomFee, fCustomFee);
		assertNotEquals(null, aCustomFee);
		assertNotEquals(aCustomFee, new Object());
		assertNotEquals(aCustomFee, bCustomFee);
		assertNotEquals(aCustomFee, cCustomFee);
		assertNotEquals(aCustomFee, dCustomFee);
		assertEquals(aCustomFee.hashCode(), fCustomFee.hashCode());
	}

	@Test
	void toStringWorks() {
		final var denom = new EntityId(111, 222, 333);
		final var fractionalFee = FcCustomFee.fractionalFee(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect,
				feeCollector);
		final var fixedHbarFee = FcCustomFee.fixedFee(fixedUnitsToCollect, null, feeCollector);
		final var fixedHtsFee = FcCustomFee.fixedFee(fixedUnitsToCollect, denom, feeCollector);
		final var expectedFractional = "FcCustomFee{feeType=FRACTIONAL_FEE, " +
				"fractionalFee=FractionalFeeSpec{numerator=5, " +
				"denominator=100, minimumUnitsToCollect=1, maximumUnitsToCollect=55}, " +
				"feeCollector=EntityId{shard=4, realm=5, num=6}}";
		final var expectedFixedHbar = "FcCustomFee{feeType=FIXED_FEE, fixedFee=FixedFeeSpec{unitsToCollect=7, " +
				"tokenDenomination=ℏ}, feeCollector=EntityId{shard=4, realm=5, num=6}}";
		final var expectedFixedHts = "FcCustomFee{feeType=FIXED_FEE, fixedFee=FixedFeeSpec{unitsToCollect=7, " +
				"tokenDenomination=111.222.333}, feeCollector=EntityId{shard=4, realm=5, num=6}}";

		assertEquals(expectedFractional, fractionalFee.toString());
		assertEquals(expectedFixedHts, fixedHtsFee.toString());
		assertEquals(expectedFixedHbar, fixedHbarFee.toString());
	}
}
