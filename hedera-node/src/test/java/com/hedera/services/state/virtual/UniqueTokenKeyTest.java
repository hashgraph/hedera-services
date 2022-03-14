package com.hedera.services.state.virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class UniqueTokenKeyTest {

	@Test
	public void constructedKey_returnsValue() {
		UniqueTokenKey key = new UniqueTokenKey(123L);
		assertThat(key.getTokenNum()).isEqualTo(123L);
	}

	@Test
	public void serializing_withDifferentTokenNums_yieldSmallerBufferPositionForLeadingZeros() throws IOException {
		UniqueTokenKey key1 = new UniqueTokenKey(0x000F);  // 1 byte
		UniqueTokenKey key2 = new UniqueTokenKey(0xFFFF);  // 2 bytes
		UniqueTokenKey key3 = new UniqueTokenKey(0xFFFFFFFF); // 4 bytes

		ByteBuffer buffer1 = ByteBuffer.wrap(new byte[UniqueTokenKey.ESTIMATED_SIZE_BYTES]);
		ByteBuffer buffer2 = ByteBuffer.wrap(new byte[UniqueTokenKey.ESTIMATED_SIZE_BYTES]);
		ByteBuffer buffer3 = ByteBuffer.wrap(new byte[UniqueTokenKey.ESTIMATED_SIZE_BYTES]);

		key1.serialize(buffer1);
		key2.serialize(buffer2);
		key3.serialize(buffer3);

		assertThat(buffer1.position()).isLessThan(buffer2.position());
		assertThat(buffer2.position()).isLessThan(buffer3.position());
	}

	private static ByteBuffer serializeToByteBuffer(long keyValue) throws IOException {
		ByteBuffer buffer = ByteBuffer.wrap(new byte[UniqueTokenKey.ESTIMATED_SIZE_BYTES]);
		new UniqueTokenKey(keyValue).serialize(buffer);
		return buffer.rewind();
	}

	@Test
	public void deserializingByteBuffer_whenCurrentVersion_restoresValueAndRegeneratesHash() throws IOException {
		UniqueTokenKey key0 = new UniqueTokenKey();
		UniqueTokenKey key1 = new UniqueTokenKey();
		UniqueTokenKey key2 = new UniqueTokenKey();
		UniqueTokenKey key3 = new UniqueTokenKey();
		UniqueTokenKey key4 = new UniqueTokenKey();
		UniqueTokenKey key5 = new UniqueTokenKey();
		UniqueTokenKey key6 = new UniqueTokenKey();
		UniqueTokenKey key7 = new UniqueTokenKey();
		UniqueTokenKey key8 = new UniqueTokenKey();

		key0.deserialize(serializeToByteBuffer(0L), UniqueTokenKey.CURRENT_VERSION);
		key1.deserialize(serializeToByteBuffer(0xFFL), UniqueTokenKey.CURRENT_VERSION);
		key2.deserialize(serializeToByteBuffer(0xFF_FFL), UniqueTokenKey.CURRENT_VERSION);
		key3.deserialize(serializeToByteBuffer(0xFF_FF_FFL), UniqueTokenKey.CURRENT_VERSION);
		key4.deserialize(serializeToByteBuffer(0xFF_FF_FF_FFL), UniqueTokenKey.CURRENT_VERSION);
		key5.deserialize(serializeToByteBuffer(0xFF_FF_FF_FF_FFL), UniqueTokenKey.CURRENT_VERSION);
		key6.deserialize(serializeToByteBuffer(0xFF_FF_FF_FF_FF_FFL), UniqueTokenKey.CURRENT_VERSION);
		key7.deserialize(serializeToByteBuffer(0xFF_FF_FF_FF_FF_FF_FFL), UniqueTokenKey.CURRENT_VERSION);
		key8.deserialize(serializeToByteBuffer(0xFF_FF_FF_FF_FF_FF_FF_FFL), UniqueTokenKey.CURRENT_VERSION);

		assertThat(key0.getTokenNum()).isEqualTo(0L);
		assertThat(key1.getTokenNum()).isEqualTo(0xFFL);
		assertThat(key2.getTokenNum()).isEqualTo(0xFF_FFL);
		assertThat(key3.getTokenNum()).isEqualTo(0xFF_FF_FFL);
		assertThat(key4.getTokenNum()).isEqualTo(0xFF_FF_FF_FFL);
		assertThat(key5.getTokenNum()).isEqualTo(0xFF_FF_FF_FF_FFL);
		assertThat(key6.getTokenNum()).isEqualTo(0xFF_FF_FF_FF_FF_FFL);
		assertThat(key7.getTokenNum()).isEqualTo(0xFF_FF_FF_FF_FF_FF_FFL);
		assertThat(key8.getTokenNum()).isEqualTo(0xFF_FF_FF_FF_FF_FF_FF_FFL);

		// Also confirm that the hash codes are mostly unique. It should be better or equal to simple int truncation
		// which will yield 5 different hashes.
		assertThat(new HashSet<>(List.of(
						key0.hashCode(), key1.hashCode(), key2.hashCode(),
						key3.hashCode(), key4.hashCode(), key5.hashCode(),
						key6.hashCode(), key7.hashCode(), key8.hashCode()))
				.size()).isAtLeast(5);
	}

	private static SerializableDataInputStream serializeToStream(long keyValue) throws IOException {
		ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream(UniqueTokenKey.ESTIMATED_SIZE_BYTES);
		SerializableDataOutputStream outputStream = new SerializableDataOutputStream(byteOutputStream);
		new UniqueTokenKey(keyValue).serialize(outputStream);

		ByteArrayInputStream inputStream = new ByteArrayInputStream(byteOutputStream.toByteArray());
		return new SerializableDataInputStream(inputStream);
	}

	@Test
	public void deserializingStream_whenCurrentVersion_restoresValueAndRegeneratesHash() throws IOException {
		UniqueTokenKey key0 = new UniqueTokenKey();
		UniqueTokenKey key1 = new UniqueTokenKey();
		UniqueTokenKey key2 = new UniqueTokenKey();
		UniqueTokenKey key3 = new UniqueTokenKey();
		UniqueTokenKey key4 = new UniqueTokenKey();
		UniqueTokenKey key5 = new UniqueTokenKey();
		UniqueTokenKey key6 = new UniqueTokenKey();
		UniqueTokenKey key7 = new UniqueTokenKey();
		UniqueTokenKey key8 = new UniqueTokenKey();

		key0.deserialize(serializeToStream(0L), UniqueTokenKey.CURRENT_VERSION);
		key1.deserialize(serializeToStream(0xFFL), UniqueTokenKey.CURRENT_VERSION);
		key2.deserialize(serializeToStream(0xFF_FFL), UniqueTokenKey.CURRENT_VERSION);
		key3.deserialize(serializeToStream(0xFF_FF_FFL), UniqueTokenKey.CURRENT_VERSION);
		key4.deserialize(serializeToStream(0xFF_FF_FF_FFL), UniqueTokenKey.CURRENT_VERSION);
		key5.deserialize(serializeToStream(0xFF_FF_FF_FF_FFL), UniqueTokenKey.CURRENT_VERSION);
		key6.deserialize(serializeToStream(0xFF_FF_FF_FF_FF_FFL), UniqueTokenKey.CURRENT_VERSION);
		key7.deserialize(serializeToStream(0xFF_FF_FF_FF_FF_FF_FFL), UniqueTokenKey.CURRENT_VERSION);
		key8.deserialize(serializeToStream(0xFF_FF_FF_FF_FF_FF_FF_FFL), UniqueTokenKey.CURRENT_VERSION);

		assertThat(key0.getTokenNum()).isEqualTo(0L);
		assertThat(key1.getTokenNum()).isEqualTo(0xFFL);
		assertThat(key2.getTokenNum()).isEqualTo(0xFF_FFL);
		assertThat(key3.getTokenNum()).isEqualTo(0xFF_FF_FFL);
		assertThat(key4.getTokenNum()).isEqualTo(0xFF_FF_FF_FFL);
		assertThat(key5.getTokenNum()).isEqualTo(0xFF_FF_FF_FF_FFL);
		assertThat(key6.getTokenNum()).isEqualTo(0xFF_FF_FF_FF_FF_FFL);
		assertThat(key7.getTokenNum()).isEqualTo(0xFF_FF_FF_FF_FF_FF_FFL);
		assertThat(key8.getTokenNum()).isEqualTo(0xFF_FF_FF_FF_FF_FF_FF_FFL);

		// Also confirm that the hash codes are mostly unique. It should be better or equal to simple int truncation
		// which will yield 5 different hashes.
		assertThat(new HashSet<>(List.of(
				key0.hashCode(), key1.hashCode(), key2.hashCode(),
				key3.hashCode(), key4.hashCode(), key5.hashCode(),
				key6.hashCode(), key7.hashCode(), key8.hashCode()))
				.size()).isAtLeast(5);
	}

	@Test
	public void deserializing_withWrongVersion_throwsException() throws IOException {
		ByteBuffer byteBuffer = serializeToByteBuffer(0xFFL);
		SerializableDataInputStream inputStream  = serializeToStream(0xFFL);

		UniqueTokenKey key = new UniqueTokenKey();
		Assertions.assertThrows(AssertionError.class,
				() -> key.deserialize(byteBuffer, UniqueTokenKey.CURRENT_VERSION + 1));

		Assertions.assertThrows(AssertionError.class,
				() -> key.deserialize(inputStream, UniqueTokenKey.CURRENT_VERSION + 1));
	}

	@Test
	public void equals_whenNull_isFalse() {
		UniqueTokenKey key = new UniqueTokenKey(123L);
		assertThat(key.equals(null)).isFalse();
	}

	@Test
	public void equals_whenDifferentType_isFalse() {
		UniqueTokenKey key = new UniqueTokenKey(123L);
		assertThat(key.equals(123L)).isFalse();
	}

	@Test
	public void equals_whenSameType_matchesContentCorrectly() {
		UniqueTokenKey key = new UniqueTokenKey(123L);
		assertThat(key.equals(new UniqueTokenKey(123L))).isTrue();
		assertThat(key.equals(new UniqueTokenKey(456L))).isFalse();
		assertThat(key.equals(new UniqueTokenKey())).isFalse();
	}

	@Test
	public void comparing_comparesProperly() {
		UniqueTokenKey key1 = new UniqueTokenKey(123L);
		UniqueTokenKey key2 = new UniqueTokenKey(456L);
		UniqueTokenKey key3 = new UniqueTokenKey(789L);

		assertThat(key1).isLessThan(key2);
		assertThat(key2).isLessThan(key3);
		assertThat(key3).isGreaterThan(key1);
		assertThat(key3).isGreaterThan(key2);
		assertThat(key2).isGreaterThan(key1);
		assertThat(key1).isEqualTo(key1);

		// In case above isEqualTo is a reference comparison, we also do the following to confirm
		assertThat(key1.compareTo(key1)).isEqualTo(0);
	}

	@Test
	public void getVersion_isCurrent() {
		UniqueTokenKey key1 = new UniqueTokenKey(123L);
		// This will fail if the version number changes and force user to update the version number here.
		assertThat(key1.getVersion()).isEqualTo(1);

		// Make sure current version is above the minimum supported version.
		assertThat(key1.getVersion()).isAtLeast(key1.getMinimumSupportedVersion());
	}

	@Test
	public void getClassId_isExpected() {
		// Make sure the class id isn't accidentally changed.
		UniqueTokenKey key1 = new UniqueTokenKey(123L);
		assertThat(key1.getClassId()).isEqualTo(0x17f77b311f6L);
	}

	@Test
	public void toString_shouldContain_tokenValue() {
		assertThat(new UniqueTokenKey(123L).toString()).contains("123");
		assertThat(new UniqueTokenKey(456L).toString()).contains("456");
	}
}
