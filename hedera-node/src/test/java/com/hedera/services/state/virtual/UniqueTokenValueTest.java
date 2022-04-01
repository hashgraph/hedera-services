package com.hedera.services.state.virtual;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.hedera.services.state.submerkle.RichInstant;
import com.swirlds.common.MutabilityException;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileCommon;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

class UniqueTokenValueTest {
	@Test
	void deserializeByteBuffer_withMetadata_returnsCorrespondingData() throws IOException {
		ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[1024]);
		UniqueTokenValue value1 = new UniqueTokenValue(
				1234L,
				5678L,
				RichInstant.fromJava(Instant.ofEpochMilli(1_000_000L)),
				"hello world".getBytes());
		value1.serialize(byteBuffer);
		byteBuffer.rewind();

		UniqueTokenValue value2 = new UniqueTokenValue();
		value2.deserialize(byteBuffer, UniqueTokenValue.CURRENT_VERSION);
		assertThat(value2.getOwnerAccountNum()).isEqualTo(1234L);
		assertThat(value2.getSpender().num()).isEqualTo(5678L);
		assertThat(value2.getCreationTime().toJava().toEpochMilli()).isEqualTo(1_000_000L);
		assertThat(value2.getMetadata()).isEqualTo("hello world".getBytes());
	}

	@Test
	void deserializeByteBuffer_withNoMetadata_returnsCorrespondingData() throws IOException {
		ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[1024]);
		UniqueTokenValue value1 = new UniqueTokenValue(
				1234L,
				5678L,
				RichInstant.fromJava(Instant.ofEpochMilli(1_000_000L)),
				new byte[0]);
		value1.serialize(byteBuffer);
		byteBuffer.rewind();

		UniqueTokenValue value2 = new UniqueTokenValue();
		value2.deserialize(byteBuffer, UniqueTokenValue.CURRENT_VERSION);
		assertThat(value2.getOwnerAccountNum()).isEqualTo(1234L);
		assertThat(value2.getSpender().num()).isEqualTo(5678L);
		assertThat(value2.getCreationTime().toJava().toEpochMilli()).isEqualTo(1_000_000L);
		assertThat(value2.getMetadata()).isEmpty();
	}

	@Test
	void deserializeDataStream_withMetadata_returnsCorrespondingData() throws IOException {
		UniqueTokenValue value1 = new UniqueTokenValue(
				1234L,
				5678L,
				RichInstant.fromJava(Instant.ofEpochMilli(1_000_000L)),
				"hello world".getBytes());
		ByteArrayOutputStream stream1 = new ByteArrayOutputStream();
		value1.serialize(new SerializableDataOutputStream(stream1));

		ByteArrayInputStream stream2 = new ByteArrayInputStream(stream1.toByteArray());
		UniqueTokenValue value2 = new UniqueTokenValue();
		value2.deserialize(new SerializableDataInputStream(stream2), UniqueTokenValue.CURRENT_VERSION);
		assertThat(value2.getOwnerAccountNum()).isEqualTo(1234L);
		assertThat(value2.getSpender().num()).isEqualTo(5678L);
		assertThat(value2.getCreationTime().toJava().toEpochMilli()).isEqualTo(1_000_000L);
		assertThat(value2.getMetadata()).isEqualTo("hello world".getBytes());
	}

	@Test
	void deserializeDataStream_withNoMetadata_returnsCorrespondingData() throws IOException {
		UniqueTokenValue value1 = new UniqueTokenValue(
				1234L,
				5678L,
				RichInstant.fromJava(Instant.ofEpochMilli(1_000_000L)),
				new byte[0]);
		ByteArrayOutputStream stream1 = new ByteArrayOutputStream();
		value1.serialize(new SerializableDataOutputStream(stream1));

		ByteArrayInputStream stream2 = new ByteArrayInputStream(stream1.toByteArray());
		UniqueTokenValue value2 = new UniqueTokenValue();
		value2.deserialize(new SerializableDataInputStream(stream2), UniqueTokenValue.CURRENT_VERSION);
		assertThat(value2.getOwnerAccountNum()).isEqualTo(1234L);
		assertThat(value2.getSpender().num()).isEqualTo(5678L);
		assertThat(value2.getCreationTime().toJava().toEpochMilli()).isEqualTo(1_000_000L);
		assertThat(value2.getMetadata()).isEmpty();
	}

	@Test
	void serialize_withMetadataExceedingMaxBytes_cappedAtMaximum() throws IOException {
		// Metadata with 100 bytes which is at the limit.
		byte[] data1 =
				"1111111111222222222233333333334444444444555555555566666666667777777777888888888899999999990000000000"
						.getBytes();
		// Metadata with 103 bytes which exceeds the limit.
		byte[] data2 =
				"1111111111222222222233333333334444444444555555555566666666667777777777888888888899999999990000000000zzz"
						.getBytes();

		UniqueTokenValue value1 = new UniqueTokenValue(
				1234L,
				5678L,
				RichInstant.fromJava(Instant.ofEpochMilli(1_000_000L)),
				data1);

		UniqueTokenValue value2 = new UniqueTokenValue(
				1234L,
				5678L,
				RichInstant.fromJava(Instant.ofEpochMilli(1_000_000L)),
				data2);

		byte[] sdata1 = new byte[1024];
		byte[] sdata2 = new byte[1024];
		value1.serialize(ByteBuffer.wrap(sdata1));
		value2.serialize(ByteBuffer.wrap(sdata2));

		// These should serialize to the same size.
		assertThat(sdata1).isEqualTo(sdata2);
	}

	@Test
	void serializeDataStream_withMetadataExceedingMaxBytes_cappedAtMaximum() throws IOException {
		// Metadata with 100 bytes which is at the limit.
		byte[] data1 =
				"1111111111222222222233333333334444444444555555555566666666667777777777888888888899999999990000000000"
						.getBytes();
		// Metadata with 103 bytes which exceeds the limit.
		byte[] data2 =
				"1111111111222222222233333333334444444444555555555566666666667777777777888888888899999999990000000000zzz"
						.getBytes();

		UniqueTokenValue value1 = new UniqueTokenValue(
				1234L,
				5678L,
				RichInstant.fromJava(Instant.ofEpochMilli(1_000_000L)),
				data1);

		UniqueTokenValue value2 = new UniqueTokenValue(
				1234L,
				5678L,
				RichInstant.fromJava(Instant.ofEpochMilli(1_000_000L)),
				data2);

		ByteArrayOutputStream stream1 = new ByteArrayOutputStream();
		ByteArrayOutputStream stream2 = new ByteArrayOutputStream();

		value1.serialize(new SerializableDataOutputStream(stream1));
		value2.serialize(new SerializableDataOutputStream(stream2));

		// These should serialize to the same size.
		assertThat(stream1.toByteArray()).isEqualTo(stream2.toByteArray());
	}

	@Test
	void deserialize_withMetadataExceedingMaxBytes_cappedAtMaximum() throws IOException {
		// Manually crafted bad data (uses version 1). Need to update if encoding changes.
		byte[] data1 = Bytes.concat(
				Longs.toByteArray(1234L),
				Longs.toByteArray(5678L),
				Longs.toByteArray(7890L),
				new byte[] {103},
				"1111111111222222222233333333334444444444555555555566666666667777777777888888888899999999990000000000zzz"
						.getBytes());

		UniqueTokenValue value1 = new UniqueTokenValue();
		UniqueTokenValue value2 = new UniqueTokenValue();
		// Deserialize with bytebuffer
		value1.deserialize(ByteBuffer.wrap(data1), 1);

		// Deserialize with datastream
		ByteArrayInputStream stream = new ByteArrayInputStream(data1);
		value2.deserialize(new SerializableDataInputStream(stream), 1);

		assertThat(value1.getMetadata()).isEqualTo(value2.getMetadata());
		assertThat(value1.getMetadata()).hasLength(100);
		assertThat(value1.getMetadata()).isEqualTo(
				"1111111111222222222233333333334444444444555555555566666666667777777777888888888899999999990000000000"
						.getBytes());
	}

	@Test
	void copiedResults_parentReleased_copyStillSame() throws IOException {
		ByteBuffer encodedEmpty = ByteBuffer.wrap(new byte[128]);
		new UniqueTokenValue().serialize(encodedEmpty);

		UniqueTokenValue src = new UniqueTokenValue(
				1234L,
				5678L,
				new RichInstant(456, 789),
				"hello world".getBytes());
		UniqueTokenValue copy = (UniqueTokenValue) src.copy();

		// Make sure parent is immutable and not modified.
		assertThat(src.isImmutable()).isTrue();
		assertThat(src.getOwnerAccountNum()).isEqualTo(1234L);
		assertThat(src.getSpender().num()).isEqualTo(5678L);
		assertThat(src.getCreationTime()).isEqualTo(RichInstant.fromJava(Instant.ofEpochSecond(456, 789)));
		assertThat(src.getMetadata()).isEqualTo("hello world".getBytes());
		src.release();

		assertThat(copy.getOwnerAccountNum()).isEqualTo(1234L);
		assertThat(copy.getSpender().num()).isEqualTo(5678L);
		assertThat(copy.getCreationTime()).isEqualTo(RichInstant.fromJava(Instant.ofEpochSecond(456, 789)));
		assertThat(copy.getMetadata()).isEqualTo("hello world".getBytes());
	}

	@Test
	void asReadOnly_mutableObject_shouldMakeItImmutable() throws IOException {
		ByteBuffer encodedEmpty = ByteBuffer.wrap(new byte[128]);
		new UniqueTokenValue().serialize(encodedEmpty);

		UniqueTokenValue src = new UniqueTokenValue(
				1234L,
				5678L,
				new RichInstant(456, 789),
				"hello world".getBytes());

		UniqueTokenValue readOnlyCopy = (UniqueTokenValue) src.asReadOnly();

		// Make sure our source copy didn't turn immutable.
		assertThat(src.isImmutable()).isFalse();

		// Mutate parent to make sure copy isn't modified.
		src.deserialize(encodedEmpty, UniqueTokenValue.CURRENT_VERSION);

		assertThat(readOnlyCopy.isImmutable()).isTrue();
		assertThat(readOnlyCopy.getOwnerAccountNum()).isEqualTo(1234L);
		assertThat(readOnlyCopy.getSpender().num()).isEqualTo(5678L);
		assertThat(readOnlyCopy.getCreationTime()).isEqualTo(RichInstant.fromJava(Instant.ofEpochSecond(456, 789)));
		assertThat(readOnlyCopy.getMetadata()).isEqualTo("hello world".getBytes());
	}

	@Test
	void asReadOnly_immutableObject_shouldStayImmutable() {
		UniqueTokenValue src = new UniqueTokenValue(
				1234L,
				5678L,
				new RichInstant(456, 789),
				"hello world".getBytes());

		UniqueTokenValue readOnlyCopy = (UniqueTokenValue) src.asReadOnly().asReadOnly();
		assertThat(readOnlyCopy.isImmutable()).isTrue();
	}

	@Test
	void deserializing_whenObjectImmutable_shouldThrowException() throws IOException {
		ByteBuffer encodedEmpty = ByteBuffer.wrap(new byte[128]);
		new UniqueTokenValue().serialize(encodedEmpty);

		UniqueTokenValue src = new UniqueTokenValue(
				1234L,
				5678L,
				new RichInstant(456, 789),
				"hello world".getBytes());

		UniqueTokenValue readOnlyCopy = (UniqueTokenValue) src.asReadOnly();
		Assertions.assertThrows(MutabilityException.class,
				() -> readOnlyCopy.deserialize(encodedEmpty, UniqueTokenValue.CURRENT_VERSION));
	}

	@Test
	void deserializingByteBuffer_withUnsupportedVersion_shouldThrowException() throws IOException {
		ByteBuffer encodedEmpty = ByteBuffer.wrap(new byte[128]);
		new UniqueTokenValue().serialize(encodedEmpty);
		UniqueTokenValue value = new UniqueTokenValue();
		Assertions.assertThrows(AssertionError.class,
				() -> value.deserialize(encodedEmpty, UniqueTokenValue.CURRENT_VERSION + 1));
	}

	@Test
	void deserializingDataStream_withUnsupportedVersion_shouldThrowException() throws IOException {
		ByteBuffer encodedEmpty = ByteBuffer.wrap(new byte[128]);
		new UniqueTokenValue().serialize(encodedEmpty);
		SerializableDataInputStream stream = new SerializableDataInputStream(
				new ByteArrayInputStream(encodedEmpty.array()));
		UniqueTokenValue value = new UniqueTokenValue();
		Assertions.assertThrows(AssertionError.class,
				() -> value.deserialize(stream, UniqueTokenValue.CURRENT_VERSION + 1));
	}

	@Test
	void toString_containsContents() {
		UniqueTokenValue value = new UniqueTokenValue(
				1234L,
				5678L,
				new RichInstant(456, 789),
				"hello world".getBytes());
		assertThat(value.toString()).contains("1234");
		assertThat(value.toString()).contains("5678");
		assertThat(value.toString()).contains(Instant.ofEpochSecond(456, 789).toString());
		assertThat(value.toString()).contains("104, 101, 108, 108, 111");
	}

	@Test
	void hashCode_mostlyUnique() {
		UniqueTokenValue value1 = new UniqueTokenValue(
				1234L,
				5678L,
				new RichInstant(456, 789),
				"hello world".getBytes());

		UniqueTokenValue value2 = new UniqueTokenValue(
				1233L,
				5678L,
				new RichInstant(455, 789),
				"hello world".getBytes());

		UniqueTokenValue value3 = new UniqueTokenValue(
				1234L,
				5678L,
				new RichInstant(456, 788),
				"hello world".getBytes());

		UniqueTokenValue value4 = new UniqueTokenValue(
				1234L,
				5678L,
				new RichInstant(456, 788),
				"hell0 world".getBytes());

		UniqueTokenValue value5 = new UniqueTokenValue(
				1234L,
				5677L,
				new RichInstant(456, 789),
				"hello world".getBytes());

		// Small mutations should lead to different hash codes.
		assertThat(new HashSet<>(List.of(
				value1, value2, value3, value4, value5
		))).hasSize(5);
	}

	@Test
	void hashCode_forCopies_areTheSame() {
		UniqueTokenValue value1 = new UniqueTokenValue(
				1234L,
				5678L,
				new RichInstant(456, 789),
				"hello world".getBytes());

		UniqueTokenValue value2 = new UniqueTokenValue(
				1234L,
				5678L,
				new RichInstant(456, 789),
				"hello world".getBytes());

		// Hashcodes are deterministic
		assertThat(value1.hashCode()).isEqualTo(value1.hashCode());

		// Hashcodes should be the same for objects constructed with same content and different instance addresses.
		assertThat(value1.hashCode()).isEqualTo(value2.hashCode());

		assertThat(value1.copy().hashCode()).isEqualTo(value1.hashCode());
		assertThat(value1.asReadOnly().hashCode()).isEqualTo(value1.hashCode());
	}

	@Test
	void equals_whenSameDataOrCopies_matches() {
		UniqueTokenValue value1 = new UniqueTokenValue(
				1234L,
				5678L,
				new RichInstant(456, 789),
				"hello world".getBytes());

		UniqueTokenValue value2 = new UniqueTokenValue(
				1234L,
				5678L,
				new RichInstant(456, 789),
				"hello world".getBytes());

		assertThat(value1).isEqualTo(value1);
		assertThat(value1).isEqualTo(value1.copy());
		assertThat(value1).isEqualTo(value1.asReadOnly());

		assertThat(value1).isEqualTo(value2);
		assertThat(value1).isEqualTo(value2.copy());
		assertThat(value1).isEqualTo(value2.asReadOnly());
	}

	@Test
	void equals_whenDifferentData_doesNotMatch() {
		UniqueTokenValue value1 = new UniqueTokenValue(
				1234L,
				5678L,
				new RichInstant(456, 789),
				"hello world".getBytes());

		UniqueTokenValue value2 = new UniqueTokenValue(
				1233L,
				5678L,
				new RichInstant(456, 789),
				"hello world".getBytes());

		UniqueTokenValue value3 = new UniqueTokenValue(
				1234L,
				5678L,
				new RichInstant(457, 789),
				"hello world".getBytes());

		UniqueTokenValue value4 = new UniqueTokenValue(
				1234L,
				5678L,
				new RichInstant(456, 780),
				"hello world".getBytes());

		UniqueTokenValue value5 = new UniqueTokenValue(
				1234L,
				5678L,
				new RichInstant(456, 789),
				"hello w0rld".getBytes());

		UniqueTokenValue value6 = new UniqueTokenValue(
				1234L,
				5677L,
				new RichInstant(456, 789),
				"hello world".getBytes());

		assertThat(value1.equals(value2)).isFalse();
		assertThat(value1.equals(value3)).isFalse();
		assertThat(value1.equals(value4)).isFalse();
		assertThat(value1.equals(value5)).isFalse();
		assertThat(value1.equals(value6)).isFalse();
	}

	@Test
	void equals_whenDifferentTypes_doesNotMatch() {
		UniqueTokenValue value = new UniqueTokenValue();
		assertThat(value.equals(new UniqueTokenKey())).isFalse();
	}

	@Test
	void equals_whenNull_doesNotMatch() {
		UniqueTokenValue value = new UniqueTokenValue();
		assertThat(value.equals(null)).isFalse();
	}

	// Test invariants. The below tests are designed to fail if one accidentally modifies specified constants.
	@Test
	void reportedSize_isVariable() {
		// This will fail if the size is accidentally swapped to a non-variable size.
		assertThat(UniqueTokenValue.sizeInBytes()).isEqualTo(DataFileCommon.VARIABLE_DATA_SIZE);
	}

	@Test
	void checkVersion_isCurrent() {
		UniqueTokenValue value = new UniqueTokenValue();
		// This will fail if the version number changes and force user to update the version number here.
		assertThat(value.getVersion()).isEqualTo(1);

		// Make sure current version is above the minimum supported version.
		assertThat(value.getVersion()).isAtLeast(value.getMinimumSupportedVersion());
	}

	@Test
	void getClassId_isExpected() {
		UniqueTokenValue value = new UniqueTokenValue();
		// Make sure the class id isn't accidentally changed.
		assertThat(value.getClassId()).isEqualTo(0xefa8762aa03ce697L);
	}
}
