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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
		assertEquals(subject.getFeeCollector(), newSubject.getFeeCollector());
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
		assertEquals(fixedSubject.getFeeCollector(), newSubject.getFeeCollector());
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
		assertEquals(fixedSubject.getFeeCollector(), newSubject.getFeeCollector());
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
		assertEquals(feeCollector, subject.getFeeCollector());
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
		assertEquals(feeCollector, subject.getFeeCollector());
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
		assertEquals(feeCollector, fixedSubject.getFeeCollector());
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
		assertEquals(feeCollector, fractionalSubject.getFeeCollector());
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
		final var desiredFixedRepr = "FixedFeeSpec{unitsToCollect=7, " +
				"tokenDenomination=EntityId{shard=1, realm=2, num=3}}";

		// expect:
		assertEquals(desiredFixedRepr, fixedSpec.toString());
		assertEquals(desiredFracRepr, fractionalSpec.toString());
	}

	@Test
	void failFastIfInvalidFractionUsed() {
		// setup:
		assertThrows(IllegalArgumentException.class, () -> new CustomFee.FractionalFeeSpec(
				validNumerator,
				invalidDenominator,
				minimumUnitsToCollect,
				maximumUnitsToCollect));
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
		assertEquals(minimumUnitsToCollect, fractionalSpec.getMinimumUnitsToCollect());
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
}
