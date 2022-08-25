/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.state.virtual;

import static com.google.common.truth.Truth.assertThat;

import com.hedera.services.store.models.NftId;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class UniqueTokenKeyTest {

    @Test
    void constructedKey_returnsValue() {
        final UniqueTokenKey key = new UniqueTokenKey(123L, 456L);
        assertThat(key.getNum()).isEqualTo(123L);
        assertThat(key.getTokenSerial()).isEqualTo(456L);
    }

    @Test
    void serializing_withDifferentTokenNums_yieldSmallerBufferPositionForLeadingZeros()
            throws IOException {
        final UniqueTokenKey key1 = new UniqueTokenKey(0, 0); // 1 byte
        final UniqueTokenKey key2 = new UniqueTokenKey(0, 0xFF); // 2 bytes
        final UniqueTokenKey key3 = new UniqueTokenKey(0xFFFF, 0); // 3 bytes
        final UniqueTokenKey key4 =
                new UniqueTokenKey(0xFFFF_FFFF_FFFF_FFFFL, 0xFFFF_FFFF_FFFF_FFFFL); // 17 bytes

        final ByteBuffer buffer1 = ByteBuffer.wrap(new byte[UniqueTokenKey.ESTIMATED_SIZE_BYTES]);
        final ByteBuffer buffer2 = ByteBuffer.wrap(new byte[UniqueTokenKey.ESTIMATED_SIZE_BYTES]);
        final ByteBuffer buffer3 = ByteBuffer.wrap(new byte[UniqueTokenKey.ESTIMATED_SIZE_BYTES]);
        final ByteBuffer buffer4 = ByteBuffer.wrap(new byte[UniqueTokenKey.ESTIMATED_SIZE_BYTES]);

        key1.serialize(buffer1);
        key2.serialize(buffer2);
        key3.serialize(buffer3);
        key4.serialize(buffer4);

        assertThat(buffer1.position()).isLessThan(buffer2.position());
        assertThat(buffer2.position()).isLessThan(buffer3.position());
        assertThat(buffer3.position()).isLessThan(buffer4.position());
    }

    private static ByteBuffer serializeToByteBuffer(final long num, final long serial)
            throws IOException {
        final ByteBuffer buffer = ByteBuffer.wrap(new byte[UniqueTokenKey.ESTIMATED_SIZE_BYTES]);
        new UniqueTokenKey(num, serial).serialize(buffer);
        return buffer.rewind();
    }

    private static UniqueTokenKey checkSerializeAndDeserializeByteBuffer(
            final long num, final long serial) throws IOException {
        final UniqueTokenKey key = new UniqueTokenKey();
        key.deserialize(serializeToByteBuffer(num, serial), UniqueTokenKey.CURRENT_VERSION);
        assertThat(key.getNum()).isEqualTo(num);
        assertThat(key.getTokenSerial()).isEqualTo(serial);
        return key;
    }

    @Test
    void deserializingByteBuffer_whenCurrentVersion_restoresValueAndRegeneratesHash()
            throws IOException {
        final List<Long> valuesToTest =
                List.of(
                        0L,
                        0xFFL,
                        0xFFFFL,
                        0xFF_FFFFL,
                        0xFFFF_FFFFL,
                        0xFF_FFFF_FFFFL,
                        0xFFFF_FFFF_FFFFL,
                        0xFF_FFFF_FFFF_FFFFL,
                        0xFFFF_FFFF_FFFF_FFFFL);
        final List<Integer> hashCodes = new ArrayList<>();
        for (final long num : valuesToTest) {
            for (final long serial : valuesToTest) {
                final UniqueTokenKey key = checkSerializeAndDeserializeByteBuffer(num, serial);
                hashCodes.add(key.hashCode());
            }
        }

        // Also confirm that the hash codes are mostly unique.
        assertThat(new HashSet<>(hashCodes).size()).isAtLeast((int) (0.7 * hashCodes.size()));
    }

    private static SerializableDataInputStream serializeToStream(final long num, final long serial)
            throws IOException {
        final ByteArrayOutputStream byteOutputStream =
                new ByteArrayOutputStream(UniqueTokenKey.ESTIMATED_SIZE_BYTES);
        final SerializableDataOutputStream outputStream =
                new SerializableDataOutputStream(byteOutputStream);
        new UniqueTokenKey(num, serial).serialize(outputStream);

        final ByteArrayInputStream inputStream =
                new ByteArrayInputStream(byteOutputStream.toByteArray());
        return new SerializableDataInputStream(inputStream);
    }

    private static UniqueTokenKey checkSerializeAndDeserializeStream(
            final long num, final long serial) throws IOException {
        final UniqueTokenKey key = new UniqueTokenKey();
        key.deserialize(serializeToStream(num, serial), UniqueTokenKey.CURRENT_VERSION);
        assertThat(key.getNum()).isEqualTo(num);
        assertThat(key.getTokenSerial()).isEqualTo(serial);
        return key;
    }

    @Test
    void deserializingStream_whenCurrentVersion_restoresValueAndRegeneratesHash()
            throws IOException {
        final List<Long> valuesToTest =
                List.of(
                        0L,
                        0xFFL,
                        0xFFFFL,
                        0xFF_FFFFL,
                        0xFFFF_FFFFL,
                        0xFF_FFFF_FFFFL,
                        0xFFFF_FFFF_FFFFL,
                        0xFF_FFFF_FFFF_FFFFL,
                        0xFFFF_FFFF_FFFF_FFFFL);
        final List<Integer> hashCodes = new ArrayList<>();
        for (final long num : valuesToTest) {
            for (final long serial : valuesToTest) {
                final UniqueTokenKey key = checkSerializeAndDeserializeStream(num, serial);
                hashCodes.add(key.hashCode());
            }
        }

        // Also confirm that the hash codes are mostly unique.
        assertThat(new HashSet<>(hashCodes).size()).isAtLeast((int) (0.7 * hashCodes.size()));
    }

    @Test
    void equals_whenNull_isFalse() {
        final UniqueTokenKey key = new UniqueTokenKey();
        assertThat(key.equals(null)).isFalse();
    }

    @Test
    void equals_whenDifferentType_isFalse() {
        final UniqueTokenKey key = new UniqueTokenKey();
        assertThat(key.equals(123L)).isFalse();
    }

    @Test
    void equals_whenSameType_matchesContentCorrectly() {
        final UniqueTokenKey key = new UniqueTokenKey(123L, 456L);
        assertThat(key.equals(new UniqueTokenKey(123L, 456L))).isTrue();
        assertThat(key.equals(new UniqueTokenKey(456L, 123L))).isFalse();
        assertThat(key.equals(new UniqueTokenKey(123L, 333L))).isFalse();
        assertThat(key.equals(new UniqueTokenKey())).isFalse();
    }

    @Test
    void comparing_comparesProperly() {
        final UniqueTokenKey key1 = new UniqueTokenKey(123L, 789L);
        final UniqueTokenKey key2 = new UniqueTokenKey(456L, 789L);
        final UniqueTokenKey key3 = new UniqueTokenKey(123L, 456L);
        final UniqueTokenKey key4 = new UniqueTokenKey(123L, 456L);

        // Check equality works
        assertThat(key1).isEqualTo(key1); // same instance
        assertThat(key3).isEqualTo(key4); // differing instances

        // Check less-than result is valid
        assertThat(key1).isLessThan(key2); // due to num field
        assertThat(key3).isLessThan(key1); // due to serial field

        // Check greater-than result is valid
        assertThat(key2).isGreaterThan(key1); // due to num field
        assertThat(key1).isGreaterThan(key3); // due to serial field

        // In case above isEqualTo is a reference comparison, we also do the following to confirm
        assertThat(key1.compareTo(key1)).isEqualTo(0); // same instance
        assertThat(key3.compareTo(key4)).isEqualTo(0); // differing instances
    }

    private static ByteBuffer asByteBuffer(final int value) {
        return ByteBuffer.wrap(new byte[] {(byte) value});
    }

    @Test
    void deserializeKeySize_withVariousPackedLengths_returnsTheCorrectLengths() {
        assertThat(UniqueTokenKey.deserializeKeySize(asByteBuffer(0))).isEqualTo(1);
        assertThat(UniqueTokenKey.deserializeKeySize(asByteBuffer(0x8))).isEqualTo(9);
        assertThat(UniqueTokenKey.deserializeKeySize(asByteBuffer(0x80))).isEqualTo(9);
        assertThat(UniqueTokenKey.deserializeKeySize(asByteBuffer(0x34))).isEqualTo(8);
        assertThat(UniqueTokenKey.deserializeKeySize(asByteBuffer(0x88))).isEqualTo(17);
    }

    @Test
    void getVersion_isCurrent() {
        final UniqueTokenKey key1 = new UniqueTokenKey();
        // This will fail if the version number changes and force user to update the version number
        // here.
        assertThat(key1.getVersion()).isEqualTo(1);

        // Make sure current version is above the minimum supported version.
        assertThat(key1.getVersion()).isAtLeast(key1.getMinimumSupportedVersion());
    }

    @Test
    void getClassId_isExpected() {
        // Make sure the class id isn't accidentally changed.
        final UniqueTokenKey key1 = new UniqueTokenKey();
        assertThat(key1.getClassId()).isEqualTo(0x17f77b311f6L);
    }

    @Test
    void toString_shouldContain_tokenValue() {
        assertThat(new UniqueTokenKey(123L, 789L).toString()).contains("123");
        assertThat(new UniqueTokenKey(123L, 789L).toString()).contains("789");
        assertThat(new UniqueTokenKey(456L, 789L).toString()).contains("456");
        assertThat(new UniqueTokenKey(456L, 789L).toString()).contains("789");
    }

    @Test
    void toEntityNumPair_isExpected() {
        final var subject = new UniqueTokenKey(1, 2).toEntityNumPair();
        assertThat(subject.getHiOrderAsLong()).isEqualTo(1);
        assertThat(subject.getLowOrderAsLong()).isEqualTo(2);
    }

    @Test
    void fromNftId_isExpected() {
        final var subject = UniqueTokenKey.from(new NftId(0, 1, 2, 3));
        assertThat(subject.getNum()).isEqualTo(2);
        assertThat(subject.getTokenSerial()).isEqualTo(3);
    }
}
