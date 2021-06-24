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

import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.Fraction;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import proto.CustomFeesOuterClass;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CustomFeeTest {
	private final long validNumerator = 5;
	private final long validDenominator = 100;
	private final long invalidDenominator = 0;
	private final long fixedUnitsToCollect = 7;
	private final long minimumUnitsToCollect = 1;
	private final long maximumUnitsToCollect = 55;
	private final EntityId denom = new EntityId(1,2, 3);
	private final EntityId feeCollector = new EntityId(4,5, 6);

	@Mock
	private SerializableDataInputStream din;
	@Mock
	private SerializableDataOutputStream dos;

	@Test
	void grpcConversionWorksForFixed() {
		// setup:
		final var grpcDenom = IdUtils.asToken("1.2.3");
		final var expectedHtsSubject = CustomFee.fixedFee(fixedUnitsToCollect, denom, feeCollector);
		final var expectedHbarSubject = CustomFee.fixedFee(fixedUnitsToCollect, null, feeCollector);

		// given:
		final var htsGrpc = CustomFeesOuterClass.CustomFee.newBuilder()
				.setFeeCollectorAccountId(feeCollector.toGrpcAccountId())
				.setFixedFee(CustomFeesOuterClass.FixedFee.newBuilder()
						.setDenominatingTokenId(grpcDenom)
						.setAmount(fixedUnitsToCollect)
				).build();
		final var hbarGrpc = CustomFeesOuterClass.CustomFee.newBuilder()
				.setFeeCollectorAccountId(feeCollector.toGrpcAccountId())
				.setFixedFee(CustomFeesOuterClass.FixedFee.newBuilder()
						.setAmount(fixedUnitsToCollect)
				).build();

		// when:
		final var htsSubject = CustomFee.fromGrpc(htsGrpc);
		final var hbarSubject = CustomFee.fromGrpc(hbarGrpc);

		// then:
		assertEquals(expectedHtsSubject, htsSubject);
		assertEquals(expectedHbarSubject, hbarSubject);
	}

	@Test
	void grpcReprWorksForFixedHbar() {
		// setup:
		final var expected = CustomFeesOuterClass.CustomFee.newBuilder()
				.setFeeCollectorAccountId(feeCollector.toGrpcAccountId())
				.setFixedFee(CustomFeesOuterClass.FixedFee.newBuilder()
						.setAmount(fixedUnitsToCollect)
				).build();

		// given:
		final var hbarFee = CustomFee.fixedFee(fixedUnitsToCollect, null, feeCollector);

		// when:
		final var actual = hbarFee.asGrpc();

		// then:
		assertEquals(expected, actual);
	}

	@Test
	void grpcReprWorksForFixedHts() {
		final var grpcDenom = IdUtils.asToken("1.2.3");

		// setup:
		final var expected = CustomFeesOuterClass.CustomFee.newBuilder()
				.setFeeCollectorAccountId(feeCollector.toGrpcAccountId())
				.setFixedFee(CustomFeesOuterClass.FixedFee.newBuilder()
						.setDenominatingTokenId(grpcDenom)
						.setAmount(fixedUnitsToCollect)
				).build();

		// given:
		final var htsFee = CustomFee.fixedFee(fixedUnitsToCollect, EntityId.fromGrpcTokenId(grpcDenom), feeCollector);

		// when:
		final var actual = htsFee.asGrpc();

		// then:
		assertEquals(expected, actual);
	}

	@Test
	void grpcReprWorksForFractional() {
		// setup:
		final var expected = CustomFeesOuterClass.CustomFee.newBuilder()
				.setFeeCollectorAccountId(feeCollector.toGrpcAccountId())
				.setFractionalFee(CustomFeesOuterClass.FractionalFee.newBuilder()
						.setFractionalAmount(Fraction.newBuilder()
								.setNumerator(validNumerator)
								.setDenominator(validDenominator))
						.setMinimumAmount(minimumUnitsToCollect)
						.setMaximumAmount(maximumUnitsToCollect)
				).build();

		// given:
		final var fractionalFee = CustomFee.fractionalFee(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect,
				feeCollector);

		// when:
		final var actual = fractionalFee.asGrpc();

		// then:
		assertEquals(expected, actual);
	}

	@Test
	void grpcReprWorksForFractionalNoMax() {
		// setup:
		final var expected = CustomFeesOuterClass.CustomFee.newBuilder()
				.setFeeCollectorAccountId(feeCollector.toGrpcAccountId())
				.setFractionalFee(CustomFeesOuterClass.FractionalFee.newBuilder()
						.setFractionalAmount(Fraction.newBuilder()
								.setNumerator(validNumerator)
								.setDenominator(validDenominator))
						.setMinimumAmount(minimumUnitsToCollect)
				).build();

		// given:
		final var fractionalFee = CustomFee.fractionalFee(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				Long.MAX_VALUE,
				feeCollector);

		// when:
		final var actual = fractionalFee.asGrpc();

		// then:
		assertEquals(expected, actual);
	}

	@Test
	void grpcConversionWorksForFractional() {
		// setup:
		final var expectedExplicitMaxSubject = CustomFee.fractionalFee(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect,
				feeCollector);
		final var expectedNoExplicitMaxSubject = CustomFee.fractionalFee(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				Long.MAX_VALUE,
				feeCollector);

		// given:
		final var grpcWithExplicitMax = CustomFeesOuterClass.CustomFee.newBuilder()
				.setFeeCollectorAccountId(feeCollector.toGrpcAccountId())
				.setFractionalFee(CustomFeesOuterClass.FractionalFee.newBuilder()
						.setFractionalAmount(Fraction.newBuilder()
								.setNumerator(validNumerator)
								.setDenominator(validDenominator)
								.build())
						.setMinimumAmount(minimumUnitsToCollect)
						.setMaximumAmount(maximumUnitsToCollect)
				).build();
		final var grpcWithoutExplicitMax = CustomFeesOuterClass.CustomFee.newBuilder()
				.setFeeCollectorAccountId(feeCollector.toGrpcAccountId())
				.setFractionalFee(CustomFeesOuterClass.FractionalFee.newBuilder()
						.setFractionalAmount(Fraction.newBuilder()
								.setNumerator(validNumerator)
								.setDenominator(validDenominator)
								.build())
						.setMinimumAmount(minimumUnitsToCollect)
				).build();

		// when:
		final var explicitMaxSubject = CustomFee.fromGrpc(grpcWithExplicitMax);
		final var noExplicitMaxSubject = CustomFee.fromGrpc(grpcWithoutExplicitMax);

		// then:
		assertEquals(expectedExplicitMaxSubject, explicitMaxSubject);
		assertEquals(expectedNoExplicitMaxSubject, noExplicitMaxSubject);
	}

	@Test
	void liveFireSerdesWorkForFractional() throws IOException {
		// setup:
		final var subject = CustomFee.fractionalFee(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect,
				feeCollector);
		// and:
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final var dos = new SerializableDataOutputStream(baos);

		// given:
		subject.serialize(dos);
		dos.flush();
		// and:
		final var bytes = baos.toByteArray();
		final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		final var din = new SerializableDataInputStream(bais);

		// when:
		final var newSubject = new CustomFee();
		newSubject.deserialize(din, CustomFee.MERKLE_VERSION);

		// then:
		assertEquals(subject.getFractionalFeeSpec(), newSubject.getFractionalFeeSpec());
		assertEquals(subject.getFeeCollectorAccountId(), newSubject.getFeeCollectorAccountId());
	}

	@Test
	void liveFireSerdesWorkForFixed() throws IOException {
		// setup:
		final var fixedSubject = CustomFee.fixedFee(fixedUnitsToCollect, denom, feeCollector);
		// and:
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final var dos = new SerializableDataOutputStream(baos);

		// given:
		fixedSubject.serialize(dos);
		dos.flush();
		// and:
		final var bytes = baos.toByteArray();
		final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		final var din = new SerializableDataInputStream(bais);

		// when:
		final var newSubject = new CustomFee();
		newSubject.deserialize(din, CustomFee.MERKLE_VERSION);

		// then:
		assertEquals(fixedSubject.getFixedFeeSpec(), newSubject.getFixedFeeSpec());
		assertEquals(fixedSubject.getFeeCollectorAccountId(), newSubject.getFeeCollectorAccountId());
	}

	@Test
	void liveFireSerdesWorkForFixedWithNullDenom() throws IOException {
		// setup:
		final var fixedSubject = CustomFee.fixedFee(fixedUnitsToCollect, null, feeCollector);
		// and:
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final var dos = new SerializableDataOutputStream(baos);

		// given:
		fixedSubject.serialize(dos);
		dos.flush();
		// and:
		final var bytes = baos.toByteArray();
		final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		final var din = new SerializableDataInputStream(bais);

		// when:
		final var newSubject = new CustomFee();
		newSubject.deserialize(din, CustomFee.MERKLE_VERSION);

		// then:
		assertEquals(fixedSubject.getFixedFeeSpec(), newSubject.getFixedFeeSpec());
		assertEquals(fixedSubject.getFeeCollectorAccountId(), newSubject.getFeeCollectorAccountId());
	}

	@Test
	void deserializeWorksAsExpectedForFixed() throws IOException {
		// setup:
		final var expectedFixedSpec = new CustomFee.FixedFeeSpec(fixedUnitsToCollect, denom);

		given(din.readByte()).willReturn(CustomFee.FIXED_CODE);
		given(din.readLong()).willReturn(fixedUnitsToCollect);
		given(din.readSerializable(anyBoolean(), Mockito.any())).willReturn(denom).willReturn(feeCollector);

		// given:
		final var subject = new CustomFee();

		// when:
		subject.deserialize(din, CustomFee.MERKLE_VERSION);

		// then:
		assertEquals(CustomFee.FeeType.FIXED_FEE, subject.getFeeType());
		assertEquals(expectedFixedSpec, subject.getFixedFeeSpec());
		assertNull(subject.getFractionalFeeSpec());
		assertEquals(feeCollector, subject.getFeeCollectorAccountId());
	}

	@Test
	void deserializeWorksAsExpectedForFractional() throws IOException {
		// setup:
		final var expectedFractionalSpec = new CustomFee.FractionalFeeSpec(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect);

		given(din.readByte()).willReturn(CustomFee.FRACTIONAL_CODE);
		given(din.readLong())
				.willReturn(validNumerator)
				.willReturn(validDenominator)
				.willReturn(minimumUnitsToCollect)
				.willReturn(maximumUnitsToCollect);
		given(din.readSerializable(anyBoolean(), Mockito.any())).willReturn(feeCollector);

		// given:
		final var subject = new CustomFee();

		// when:
		subject.deserialize(din, CustomFee.MERKLE_VERSION);

		// then:
		assertEquals(CustomFee.FeeType.FRACTIONAL_FEE, subject.getFeeType());
		assertEquals(expectedFractionalSpec, subject.getFractionalFeeSpec());
		assertNull(subject.getFixedFeeSpec());
		assertEquals(feeCollector, subject.getFeeCollectorAccountId());
	}

	@Test
	void serializeWorksAsExpectedForFractional() throws IOException {
		// setup:
		InOrder inOrder = Mockito.inOrder(dos);

		// given:
		final var subject = CustomFee.fractionalFee(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect,
				feeCollector);

		// when:
		subject.serialize(dos);

		// then:
		inOrder.verify(dos).writeByte(CustomFee.FRACTIONAL_CODE);
		inOrder.verify(dos).writeLong(validNumerator);
		inOrder.verify(dos).writeLong(validDenominator);
		inOrder.verify(dos).writeLong(minimumUnitsToCollect);
		inOrder.verify(dos).writeLong(maximumUnitsToCollect);
		inOrder.verify(dos).writeSerializable(feeCollector, true);
	}

	@Test
	void serializeWorksAsExpectedForFixed() throws IOException {
		// setup:
		InOrder inOrder = Mockito.inOrder(dos);

		// given:
		final var subject = CustomFee.fixedFee(fixedUnitsToCollect, denom, feeCollector);

		// when:
		subject.serialize(dos);

		// then:
		inOrder.verify(dos).writeByte(CustomFee.FIXED_CODE);
		inOrder.verify(dos).writeLong(fixedUnitsToCollect);
		inOrder.verify(dos).writeSerializable(denom, true);
		inOrder.verify(dos).writeSerializable(feeCollector, true);
	}


	@Test
	void merkleMethodsWork() {
		// given:
		final var subject = CustomFee.fixedFee(fixedUnitsToCollect, denom, feeCollector);

		assertEquals(CustomFee.MERKLE_VERSION, subject.getVersion());
		assertEquals(CustomFee.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	@Test
	void fixedFactoryWorks() {
		// setup:
		final var expectedFixedSpec = new CustomFee.FixedFeeSpec(fixedUnitsToCollect, denom);

		// given:
		final var fixedSubject = CustomFee.fixedFee(fixedUnitsToCollect, denom, feeCollector);

		// expect:
		assertEquals(CustomFee.FeeType.FIXED_FEE, fixedSubject.getFeeType());
		assertEquals(expectedFixedSpec, fixedSubject.getFixedFeeSpec());
		assertNull(fixedSubject.getFractionalFeeSpec());
		assertEquals(feeCollector, fixedSubject.getFeeCollectorAccountId());
	}

	@Test
	void fractionalFactoryWorks() {
		// setup:
		final var expectedFractionalSpec = new CustomFee.FractionalFeeSpec(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect);

		// given:
		final var fractionalSubject = CustomFee.fractionalFee(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect,
				feeCollector);

		// expect:
		assertEquals(CustomFee.FeeType.FRACTIONAL_FEE, fractionalSubject.getFeeType());
		assertEquals(expectedFractionalSpec, fractionalSubject.getFractionalFeeSpec());
		assertNull(fractionalSubject.getFixedFeeSpec());
		assertEquals(feeCollector, fractionalSubject.getFeeCollectorAccountId());
	}

	@Test
	void toStringsWork() {
		// setup:
		final var fractionalSpec = new CustomFee.FractionalFeeSpec(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect);
		final var fixedSpec = new CustomFee.FixedFeeSpec(fixedUnitsToCollect, denom);

		// given:
		final var desiredFracRepr = "FractionalFeeSpec{numerator=5, denominator=100, " +
				"minimumUnitsToCollect=1, maximumUnitsToCollect=55}";
		final var desiredFixedRepr = "FixedFeeSpec{unitsToCollect=7, tokenDenomination=1.2.3}";

		// expect:
		assertEquals(desiredFixedRepr, fixedSpec.toString());
		assertEquals(desiredFracRepr, fractionalSpec.toString());
	}

	@Test
	void failsFastIfNonPositiveFeeUsed() {
		assertThrows(IllegalArgumentException.class, () -> new CustomFee.FixedFeeSpec(0, denom));
		assertThrows(IllegalArgumentException.class, () -> new CustomFee.FixedFeeSpec(-1, denom));
	}

	@Test
	void failFastIfInvalidFractionUsed() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> new CustomFee.FractionalFeeSpec(
				validNumerator,
				invalidDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect));
		assertThrows(IllegalArgumentException.class, () -> new CustomFee.FractionalFeeSpec(
				-validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect));
		assertThrows(IllegalArgumentException.class, () -> new CustomFee.FractionalFeeSpec(
				validNumerator,
				-validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect));
		assertThrows(IllegalArgumentException.class, () -> new CustomFee.FractionalFeeSpec(
				validNumerator,
				validDenominator,
				-minimumUnitsToCollect,
				maximumUnitsToCollect));
		assertThrows(IllegalArgumentException.class, () -> new CustomFee.FractionalFeeSpec(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				-maximumUnitsToCollect));
		assertThrows(IllegalArgumentException.class, () -> new CustomFee.FractionalFeeSpec(
				validNumerator,
				validDenominator,
				maximumUnitsToCollect,
				minimumUnitsToCollect));
	}

	@Test
	void gettersWork() {
		// setup:
		final var fractionalSpec = new CustomFee.FractionalFeeSpec(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect);
		final var fixedSpec = new CustomFee.FixedFeeSpec(fixedUnitsToCollect, denom);

		// given:
		assertEquals(validNumerator, fractionalSpec.getNumerator());
		assertEquals(validDenominator, fractionalSpec.getDenominator());
		assertEquals(minimumUnitsToCollect, fractionalSpec.getMinimumAmount());
		assertEquals(maximumUnitsToCollect, fractionalSpec.getMaximumUnitsToCollect());
		assertEquals(fixedUnitsToCollect, fixedSpec.getUnitsToCollect());
		assertEquals(denom, fixedSpec.getTokenDenomination());
	}

	@Test
	void hashCodeWorks() {
		// setup:
		final var fractionalSpec = new CustomFee.FractionalFeeSpec(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect);
		final var fixedSpec = new CustomFee.FixedFeeSpec(fixedUnitsToCollect, denom);

		// expect:
		assertDoesNotThrow(fractionalSpec::hashCode);
		assertDoesNotThrow(fixedSpec::hashCode);
	}

	@Test
	void fixedFeeEqualsWorks() {
		// given:
		final var aFixedSpec = new CustomFee.FixedFeeSpec(fixedUnitsToCollect, denom);
		final var bFixedSpec = new CustomFee.FixedFeeSpec(fixedUnitsToCollect, denom);
		final var cFixedSpec = new CustomFee.FixedFeeSpec(fixedUnitsToCollect + 1, denom);
		final var dFixedSpec = new CustomFee.FixedFeeSpec(fixedUnitsToCollect, null);
		final var eFixedSpec = aFixedSpec;

		// expect:
		assertEquals(aFixedSpec, bFixedSpec);
		assertEquals(aFixedSpec, eFixedSpec);
		assertNotEquals(aFixedSpec, null);
		assertNotEquals(aFixedSpec, new Object());
		assertNotEquals(aFixedSpec, cFixedSpec);
		assertNotEquals(aFixedSpec, dFixedSpec);
	}

	@Test
	void fractionalFeeEqualsWorks() {
		// setup:
		long n = 3;
		long d = 7;
		long min = 22;
		long max = 99;

		// given:
		final var aFractionalSpec = new CustomFee.FractionalFeeSpec(n, d, min, max);
		final var bFractionalSpec = new CustomFee.FractionalFeeSpec(n + 1, d, min, max);
		final var cFractionalSpec = new CustomFee.FractionalFeeSpec(n, d + 1, min, max);
		final var dFractionalSpec = new CustomFee.FractionalFeeSpec(n, d, min + 1, max);
		final var eFractionalSpec = new CustomFee.FractionalFeeSpec(n, d, min, max + 1);
		final var fFractionalSpec = new CustomFee.FractionalFeeSpec(n, d, min, max);
		final var gFractionalSpec = aFractionalSpec;

		// expect:
		assertEquals(aFractionalSpec, fFractionalSpec);
		assertEquals(aFractionalSpec, gFractionalSpec);
		assertNotEquals(aFractionalSpec, null);
		assertNotEquals(aFractionalSpec, new Object());
		assertNotEquals(aFractionalSpec, bFractionalSpec);
		assertNotEquals(aFractionalSpec, cFractionalSpec);
		assertNotEquals(aFractionalSpec, dFractionalSpec);
		assertNotEquals(aFractionalSpec, eFractionalSpec);
	}

	@Test
	void customFeeEqualsWorks() {
		// setup:
		long n = 3;
		long d = 7;
		long min = 22;
		long max = 99;
		// and:
		final var aFeeCollector = new EntityId(1, 2, 3);
		final var bFeeCollector = new EntityId(2, 3, 4);

		// given:
		final var aCustomFee = CustomFee.fixedFee(fixedUnitsToCollect, denom, aFeeCollector);
		final var bCustomFee = CustomFee.fixedFee(fixedUnitsToCollect + 1, denom, aFeeCollector);
		final var cCustomFee = CustomFee.fixedFee(fixedUnitsToCollect, denom, bFeeCollector);
		final var dCustomFee = CustomFee.fractionalFee(n, d, min, max, aFeeCollector);
		final var eCustomFee = aCustomFee;
		final var fCustomFee = CustomFee.fixedFee(fixedUnitsToCollect, denom, aFeeCollector);

		// expect:
		assertEquals(aCustomFee, eCustomFee);
		assertEquals(aCustomFee, fCustomFee);
		assertNotEquals(aCustomFee, null);
		assertNotEquals(aCustomFee, new Object());
		assertNotEquals(aCustomFee, bCustomFee);
		assertNotEquals(aCustomFee, cCustomFee);
		assertNotEquals(aCustomFee, dCustomFee);
		// and:
		assertEquals(aCustomFee.hashCode(), fCustomFee.hashCode());
	}

	@Test
	void toStringWorks() {
		// setup:
		final var denom = new EntityId(111, 222, 333);
		final var fractionalFee = CustomFee.fractionalFee(
				validNumerator,
				validDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect,
				feeCollector);
		final var fixedHbarFee = CustomFee.fixedFee(fixedUnitsToCollect, null, feeCollector);
		final var fixedHtsFee = CustomFee.fixedFee(fixedUnitsToCollect, denom, feeCollector);

		// given:
		final var expectedFractional = "CustomFee{feeType=FRACTIONAL_FEE, fractionalFee=FractionalFeeSpec{numerator=5, " +
				"denominator=100, minimumUnitsToCollect=1, maximumUnitsToCollect=55}, " +
				"feeCollector=EntityId{shard=4, realm=5, num=6}}";
		final var expectedFixedHbar = "CustomFee{feeType=FIXED_FEE, fixedFee=FixedFeeSpec{unitsToCollect=7, " +
				"tokenDenomination=ℏ}, feeCollector=EntityId{shard=4, realm=5, num=6}}";
		final var expectedFixedHts = "CustomFee{feeType=FIXED_FEE, fixedFee=FixedFeeSpec{unitsToCollect=7, " +
				"tokenDenomination=111.222.333}, feeCollector=EntityId{shard=4, realm=5, num=6}}";

		// expect:
		assertEquals(expectedFractional, fractionalFee.toString());
		assertEquals(expectedFixedHts, fixedHtsFee.toString());
		assertEquals(expectedFixedHbar, fixedHbarFee.toString());
	}
}
