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
import static com.hedera.services.utils.subjects.UniqueTokenValueSubject.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.NftNumPair;
import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileCommon;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UniqueTokenValueTest {

    private UniqueTokenValue example1;
    private UniqueTokenValue example2;

    @BeforeEach
    void setUp() {
        example1 =
                new UniqueTokenValue(
                        1234L,
                        5678L,
                        "hello world".getBytes(),
                        RichInstant.fromJava(Instant.ofEpochMilli(1_000_000L)));
        example1.setPrev(NftNumPair.fromLongs(111L, 333L));
        example1.setNext(NftNumPair.fromLongs(222L, 444L));

        example2 =
                new UniqueTokenValue(
                        1234L,
                        5678L,
                        new byte[0],
                        RichInstant.fromJava(Instant.ofEpochMilli(1_000_000L)));
    }

    private void assertSameAsExample1(final UniqueTokenValue actual) {
        assertThat(actual).hasOwner(1234L);
        assertThat(actual).hasSpender(5678L);
        assertThat(actual).hasCreationTime(Instant.ofEpochMilli(1_000_000L));
        assertThat(actual).hasMetadata("hello world".getBytes());
        assertThat(actual).hasPrev(111L, 333L);
        assertThat(actual).hasNext(222L, 444L);
    }

    private void assertSameAsExample2(final UniqueTokenValue actual) {
        assertThat(actual).hasOwner(1234L);
        assertThat(actual).hasSpender(5678L);
        assertThat(actual).hasCreationTime(Instant.ofEpochMilli(1_000_000L));
        assertThat(actual).metadata().isEmpty();
        assertThat(actual).hasPrev(NftNumPair.MISSING_NFT_NUM_PAIR);
        assertThat(actual).hasNext(NftNumPair.MISSING_NFT_NUM_PAIR);
    }

    @Test
    void deserializeByteBuffer_withMetadata_returnsCorrespondingData() throws IOException {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[1024]);
        example1.serialize(byteBuffer);
        byteBuffer.rewind();

        final UniqueTokenValue value = new UniqueTokenValue();
        value.deserialize(byteBuffer, UniqueTokenValue.CURRENT_VERSION);
        assertSameAsExample1(value);
    }

    @Test
    void deserializeByteBuffer_withNoMetadata_returnsCorrespondingData() throws IOException {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[1024]);
        example2.serialize(byteBuffer);
        byteBuffer.rewind();

        final UniqueTokenValue value = new UniqueTokenValue();
        value.deserialize(byteBuffer, UniqueTokenValue.CURRENT_VERSION);
        assertSameAsExample2(value);
    }

    @Test
    void deserializeDataStream_withMetadata_returnsCorrespondingData() throws IOException {
        final ByteArrayOutputStream stream1 = new ByteArrayOutputStream();
        example1.serialize(new SerializableDataOutputStream(stream1));

        final ByteArrayInputStream stream2 = new ByteArrayInputStream(stream1.toByteArray());
        final UniqueTokenValue value = new UniqueTokenValue();
        value.deserialize(
                new SerializableDataInputStream(stream2), UniqueTokenValue.CURRENT_VERSION);
        assertSameAsExample1(value);
    }

    @Test
    void deserializeDataStream_withNoMetadata_returnsCorrespondingData() throws IOException {
        final ByteArrayOutputStream stream1 = new ByteArrayOutputStream();
        example2.serialize(new SerializableDataOutputStream(stream1));

        final ByteArrayInputStream stream2 = new ByteArrayInputStream(stream1.toByteArray());
        final UniqueTokenValue value = new UniqueTokenValue();
        value.deserialize(
                new SerializableDataInputStream(stream2), UniqueTokenValue.CURRENT_VERSION);
        assertSameAsExample2(value);
    }

    @Test
    void serialize_withMetadataExceedingMaxBytes_cappedAtMaximum() throws IOException {
        // Metadata with 100 bytes which is at the limit.
        final byte[] data1 =
                "1111111111222222222233333333334444444444555555555566666666667777777777888888888899999999990000000000"
                        .getBytes();
        // Metadata with 103 bytes which exceeds the limit.
        final byte[] data2 =
                "1111111111222222222233333333334444444444555555555566666666667777777777888888888899999999990000000000zzz"
                        .getBytes();

        final UniqueTokenValue value1 =
                new UniqueTokenValue(
                        1234L,
                        5678L,
                        data1,
                        RichInstant.fromJava(Instant.ofEpochMilli(1_000_000L)));

        final UniqueTokenValue value2 =
                new UniqueTokenValue(
                        1234L,
                        5678L,
                        data2,
                        RichInstant.fromJava(Instant.ofEpochMilli(1_000_000L)));

        final byte[] sdata1 = new byte[1024];
        final byte[] sdata2 = new byte[1024];
        value1.serialize(ByteBuffer.wrap(sdata1));
        value2.serialize(ByteBuffer.wrap(sdata2));

        // These should serialize to the same size.
        assertThat(sdata1).isEqualTo(sdata2);
    }

    @Test
    void serializeDataStream_withMetadataExceedingMaxBytes_cappedAtMaximum() throws IOException {
        // Metadata with 100 bytes which is at the limit.
        final byte[] data1 =
                "1111111111222222222233333333334444444444555555555566666666667777777777888888888899999999990000000000"
                        .getBytes();
        // Metadata with 103 bytes which exceeds the limit.
        final byte[] data2 =
                "1111111111222222222233333333334444444444555555555566666666667777777777888888888899999999990000000000zzz"
                        .getBytes();

        final UniqueTokenValue value1 =
                new UniqueTokenValue(
                        1234L,
                        5678L,
                        data1,
                        RichInstant.fromJava(Instant.ofEpochMilli(1_000_000L)));

        final UniqueTokenValue value2 =
                new UniqueTokenValue(
                        1234L,
                        5678L,
                        data2,
                        RichInstant.fromJava(Instant.ofEpochMilli(1_000_000L)));

        final ByteArrayOutputStream stream1 = new ByteArrayOutputStream();
        final ByteArrayOutputStream stream2 = new ByteArrayOutputStream();

        value1.serialize(new SerializableDataOutputStream(stream1));
        value2.serialize(new SerializableDataOutputStream(stream2));

        // These should serialize to the same size.
        assertThat(stream1.toByteArray()).isEqualTo(stream2.toByteArray());
    }

    @Test
    void deserialize_withMetadataExceedingMaxBytes_cappedAtMaximum() throws IOException {
        // Manually crafted bad data (uses version 1). Need to update if encoding changes.
        final byte[] data1 =
                Bytes.concat(
                        Longs.toByteArray(1234L),
                        Longs.toByteArray(5678L),
                        Longs.toByteArray(7890L),
                        new byte[] {103},
                        "1111111111222222222233333333334444444444555555555566666666667777777777888888888899999999990000000000zzz"
                                .getBytes(),
                        Longs.toByteArray(111L),
                        Longs.toByteArray(222L),
                        Longs.toByteArray(333L),
                        Longs.toByteArray(444L));

        final UniqueTokenValue value1 = new UniqueTokenValue();
        final UniqueTokenValue value2 = new UniqueTokenValue();
        // Deserialize with bytebuffer
        value1.deserialize(ByteBuffer.wrap(data1), 1);

        // Deserialize with datastream
        final ByteArrayInputStream stream = new ByteArrayInputStream(data1);
        value2.deserialize(new SerializableDataInputStream(stream), 1);

        assertThat(value1.getMetadata()).isEqualTo(value2.getMetadata());
        assertThat(value1.getMetadata()).hasLength(100);
        assertThat(value1.getMetadata())
                .isEqualTo(
                        "1111111111222222222233333333334444444444555555555566666666667777777777888888888899999999990000000000"
                                .getBytes());
    }

    @Test
    void copiedResults_parentReleased_copyStillSame() throws IOException {
        final ByteBuffer encodedEmpty = ByteBuffer.wrap(new byte[128]);
        new UniqueTokenValue().serialize(encodedEmpty);

        final UniqueTokenValue src =
                new UniqueTokenValue(
                        1234L, 5678L, "hello world".getBytes(), new RichInstant(456, 789));
        final UniqueTokenValue copy = (UniqueTokenValue) src.copy();

        // Make sure parent is immutable and not modified.
        assertThat(src.isImmutable()).isTrue();
        assertThat(src.getOwnerAccountNum()).isEqualTo(1234L);
        assertThat(src.getSpender().num()).isEqualTo(5678L);
        assertThat(src.getCreationTime())
                .isEqualTo(RichInstant.fromJava(Instant.ofEpochSecond(456, 789)));
        assertThat(src.getMetadata()).isEqualTo("hello world".getBytes());
        src.release();

        assertThat(copy.getOwnerAccountNum()).isEqualTo(1234L);
        assertThat(copy.getSpender().num()).isEqualTo(5678L);
        assertThat(copy.getCreationTime())
                .isEqualTo(RichInstant.fromJava(Instant.ofEpochSecond(456, 789)));
        assertThat(copy.getMetadata()).isEqualTo("hello world".getBytes());
    }

    @Test
    void asReadOnly_mutableObject_shouldMakeItImmutable() throws IOException {
        final ByteBuffer encodedEmpty = ByteBuffer.wrap(new byte[128]);
        new UniqueTokenValue().serialize(encodedEmpty);

        final UniqueTokenValue src =
                new UniqueTokenValue(
                        1234L, 5678L, "hello world".getBytes(), new RichInstant(456, 789));

        final UniqueTokenValue readOnlyCopy = (UniqueTokenValue) src.asReadOnly();

        // Make sure our source copy didn't turn immutable.
        assertThat(src.isImmutable()).isFalse();

        // Mutate parent to make sure copy isn't modified.
        src.deserialize(encodedEmpty, UniqueTokenValue.CURRENT_VERSION);

        assertThat(readOnlyCopy.isImmutable()).isTrue();
        assertThat(readOnlyCopy.getOwnerAccountNum()).isEqualTo(1234L);
        assertThat(readOnlyCopy.getSpender().num()).isEqualTo(5678L);
        assertThat(readOnlyCopy.getCreationTime())
                .isEqualTo(RichInstant.fromJava(Instant.ofEpochSecond(456, 789)));
        assertThat(readOnlyCopy.getMetadata()).isEqualTo("hello world".getBytes());
    }

    @Test
    void asReadOnly_immutableObject_shouldStayImmutable() {
        final UniqueTokenValue src =
                new UniqueTokenValue(
                        1234L, 5678L, "hello world".getBytes(), new RichInstant(456, 789));

        final UniqueTokenValue readOnlyCopy = (UniqueTokenValue) src.asReadOnly().asReadOnly();
        assertThat(readOnlyCopy.isImmutable()).isTrue();
    }

    @Test
    void deserializing_whenObjectImmutable_shouldThrowException() throws IOException {
        final ByteBuffer encodedEmpty = ByteBuffer.wrap(new byte[128]);
        new UniqueTokenValue().serialize(encodedEmpty);

        final UniqueTokenValue src =
                new UniqueTokenValue(
                        1234L, 5678L, "hello world".getBytes(), new RichInstant(456, 789));

        final UniqueTokenValue readOnlyCopy = (UniqueTokenValue) src.asReadOnly();
        assertThrows(
                MutabilityException.class,
                () -> readOnlyCopy.deserialize(encodedEmpty, UniqueTokenValue.CURRENT_VERSION));
    }

    @Test
    void toString_containsContents() {
        final UniqueTokenValue value =
                new UniqueTokenValue(
                        1234L, 5678L, "hello world".getBytes(), new RichInstant(456, 789));
        value.setPrev(NftNumPair.fromLongs(1111L, 2222L));
        value.setNext(NftNumPair.fromLongs(333L, 444L));
        assertThat(value.toString()).contains("1234");
        assertThat(value.toString()).contains("5678");
        assertThat(value.toString())
                .contains(RichInstant.fromJava(Instant.ofEpochSecond(456, 789)).toString());
        assertThat(value.toString()).contains("104, 101, 108, 108, 111");
        assertThat(value.toString()).contains("prev=0.0.1111.2222");
        assertThat(value.toString()).contains("next=0.0.333.444");
    }

    @Test
    void hashCode_mostlyUnique() {
        final UniqueTokenValue value1 =
                new UniqueTokenValue(
                        1234L, 5678L, "hello world".getBytes(), new RichInstant(456, 789));

        final UniqueTokenValue value2 =
                new UniqueTokenValue(
                        1233L, 5678L, "hello world".getBytes(), new RichInstant(455, 789));

        final UniqueTokenValue value3 =
                new UniqueTokenValue(
                        1234L, 5678L, "hello world".getBytes(), new RichInstant(456, 788));

        final UniqueTokenValue value4 =
                new UniqueTokenValue(
                        1234L, 5678L, "hell0 world".getBytes(), new RichInstant(456, 788));

        final UniqueTokenValue value5 =
                new UniqueTokenValue(
                        1234L, 5677L, "hello world".getBytes(), new RichInstant(456, 789));

        // Small mutations should lead to different hash codes.
        assertThat(new HashSet<>(List.of(value1, value2, value3, value4, value5))).hasSize(5);
    }

    @Test
    void hashCode_forCopies_areTheSame() {
        final UniqueTokenValue value1 =
                new UniqueTokenValue(
                        1234L, 5678L, "hello world".getBytes(), new RichInstant(456, 789));

        final UniqueTokenValue value2 =
                new UniqueTokenValue(
                        1234L, 5678L, "hello world".getBytes(), new RichInstant(456, 789));

        // Hashcodes are deterministic
        assertThat(value1.hashCode()).isEqualTo(value1.hashCode());

        // Hashcodes should be the same for objects constructed with same content and different
        // instance addresses.
        assertThat(value1.hashCode()).isEqualTo(value2.hashCode());

        assertThat(value1.copy().hashCode()).isEqualTo(value1.hashCode());
        assertThat(value1.asReadOnly().hashCode()).isEqualTo(value1.hashCode());
    }

    @Test
    void equals_whenSameDataOrCopies_matches() {
        final UniqueTokenValue value1 =
                new UniqueTokenValue(
                        1234L, 5678L, "hello world".getBytes(), new RichInstant(456, 789));
        value1.setPrev(NftNumPair.fromLongs(1, 2));
        value1.setNext(NftNumPair.fromLongs(3, 4));

        final UniqueTokenValue value2 =
                new UniqueTokenValue(
                        1234L, 5678L, "hello world".getBytes(), new RichInstant(456, 789));
        value2.setPrev(NftNumPair.fromLongs(1, 2));
        value2.setNext(NftNumPair.fromLongs(3, 4));

        assertThat(value1).isEqualTo(value1);
        assertThat(value1).isEqualTo(value1.copy());
        assertThat(value1).isEqualTo(value1.asReadOnly());

        assertThat(value1).isEqualTo(value2);
        assertThat(value1).isEqualTo(value2.copy());
        assertThat(value1).isEqualTo(value2.asReadOnly());
    }

    @Test
    void equals_whenDifferentData_doesNotMatch() {
        final UniqueTokenValue value1 =
                new UniqueTokenValue(
                        1234L, 5678L, "hello world".getBytes(), new RichInstant(456, 789));

        final UniqueTokenValue value2 =
                new UniqueTokenValue(
                        1233L, 5678L, "hello world".getBytes(), new RichInstant(456, 789));

        final UniqueTokenValue value3 =
                new UniqueTokenValue(
                        1234L, 5678L, "hello world".getBytes(), new RichInstant(457, 789));

        final UniqueTokenValue value4 =
                new UniqueTokenValue(
                        1234L, 5678L, "hello world".getBytes(), new RichInstant(456, 780));

        final UniqueTokenValue value5 =
                new UniqueTokenValue(
                        1234L, 5678L, "hello w0rld".getBytes(), new RichInstant(456, 789));

        final UniqueTokenValue value6 =
                new UniqueTokenValue(
                        1234L, 5677L, "hello world".getBytes(), new RichInstant(456, 789));

        final UniqueTokenValue value7 =
                new UniqueTokenValue(
                        1234L, 5677L, "hello world".getBytes(), new RichInstant(456, 789));
        value7.setPrev(NftNumPair.fromLongs(1, 2));

        final UniqueTokenValue value8 =
                new UniqueTokenValue(
                        1234L, 5677L, "hello world".getBytes(), new RichInstant(456, 789));
        value8.setPrev(NftNumPair.fromLongs(3, 4));

        final UniqueTokenValue value9 =
                new UniqueTokenValue(
                        1234L, 5677L, "hello world".getBytes(), new RichInstant(456, 789));
        value9.setNext(NftNumPair.fromLongs(1, 2));

        final UniqueTokenValue value10 =
                new UniqueTokenValue(
                        1234L, 5677L, "hello world".getBytes(), new RichInstant(456, 789));
        value10.setNext(NftNumPair.fromLongs(3, 4));

        assertThat(value1.equals(value2)).isFalse();
        assertThat(value1.equals(value3)).isFalse();
        assertThat(value1.equals(value4)).isFalse();
        assertThat(value1.equals(value5)).isFalse();
        assertThat(value1.equals(value6)).isFalse();
        assertThat(value7.equals(value8)).isFalse();
        assertThat(value7.equals(value9)).isFalse();
        assertThat(value9.equals(value10)).isFalse();
    }

    @Test
    void equals_whenDifferentTypes_doesNotMatch() {
        final UniqueTokenValue value = new UniqueTokenValue();
        assertThat(value.equals(new UniqueTokenKey())).isFalse();
    }

    @Test
    void equals_whenNull_doesNotMatch() {
        final UniqueTokenValue value = new UniqueTokenValue();
        assertThat(value.equals(null)).isFalse();
    }

    // Test invariants. The below tests are designed to fail if one accidentally modifies specified
    // constants.
    @Test
    void reportedSize_isVariable() {
        // This will fail if the size is accidentally swapped to a non-variable size.
        assertThat(UniqueTokenValue.sizeInBytes()).isEqualTo(DataFileCommon.VARIABLE_DATA_SIZE);
    }

    @Test
    void checkVersion_isCurrent() {
        final UniqueTokenValue value = new UniqueTokenValue();
        // This will fail if the version number changes and force user to update the version number
        // here.
        assertThat(value.getVersion()).isEqualTo(1);

        // Make sure current version is above the minimum supported version.
        assertThat(value.getVersion()).isAtLeast(value.getMinimumSupportedVersion());
    }

    @Test
    void getClassId_isExpected() {
        final UniqueTokenValue value = new UniqueTokenValue();
        // Make sure the class id isn't accidentally changed.
        assertThat(value.getClassId()).isEqualTo(0xefa8762aa03ce697L);
    }

    @Test
    void constructorWithMerkleToken_createsAssociatedToken() {
        final MerkleUniqueToken legacyToken = new MerkleUniqueToken();
        legacyToken.setNext(NftNumPair.fromLongs(1, 2));
        legacyToken.setPrev(NftNumPair.fromLongs(3, 4));
        legacyToken.setOwner(EntityId.fromNum(5));
        legacyToken.setPackedCreationTime(6L);
        legacyToken.setSpender(EntityId.fromNum(7L));
        legacyToken.setMetadata(new byte[] {8, 9, 10});
        final UniqueTokenValue token = UniqueTokenValue.from(legacyToken);
        assertThat(token).hasNext(1, 2);
        assertThat(token).hasPrev(3, 4);
        assertThat(token).hasOwner(5);
        assertThat(token).hasPackedCreationTime(6);
        assertThat(token).hasSpender(7);
        assertThat(token).hasMetadata(new byte[] {8, 9, 10});
    }

    @Test
    void setGetOwner_whenMutable_worksAsExpected() {
        final UniqueTokenValue value = new UniqueTokenValue();
        assertThat(value).hasOwner(EntityId.MISSING_ENTITY_ID);
        value.setOwner(EntityId.fromNum(4));
        assertThat(value.getOwner()).isEqualTo(EntityId.fromNum(4));
        value.setOwner(EntityId.fromNum(5));
        assertThat(value.getOwner()).isEqualTo(EntityId.fromNum(5));
    }

    @Test
    void setGetSpender_whenMutable_worksAsExpected() {
        final UniqueTokenValue value = new UniqueTokenValue();
        assertThat(value).hasSpender(EntityId.MISSING_ENTITY_ID);
        value.setSpender(EntityId.fromNum(4));
        assertThat(value.getSpender()).isEqualTo(EntityId.fromNum(4));
        value.setSpender(EntityId.fromNum(5));
        assertThat(value.getSpender()).isEqualTo(EntityId.fromNum(5));
    }

    @Test
    void setGetMetadata_whenMutable_worksAsExpected() {
        final UniqueTokenValue value = new UniqueTokenValue();
        value.setMetadata("hello".getBytes());
        assertThat(value.getMetadata()).isEqualTo("hello".getBytes());
    }

    @Test
    void setGetPackedCreationTime_whenMutable_worksAsExpected() {
        final UniqueTokenValue value = new UniqueTokenValue();
        value.setPackedCreationTime(123);
        assertThat(value.getPackedCreationTime()).isEqualTo(123);
    }

    @Test
    void mutations_whenImmutable_throwsException() {
        final var value = new UniqueTokenValue();
        final var entityId = EntityId.fromNum(1);
        final var nftNumPair = NftNumPair.fromLongs(1, 1);
        final var bytes = new byte[] {0, 1};

        value.copy(); // make value immutable
        assertThrows(MutabilityException.class, () -> value.setOwner(entityId));
        assertThrows(MutabilityException.class, () -> value.setSpender(entityId));
        assertThrows(MutabilityException.class, () -> value.setMetadata(bytes));
        assertThrows(MutabilityException.class, () -> value.setPackedCreationTime(1));
        assertThrows(MutabilityException.class, () -> value.setPrev(nftNumPair));
        assertThrows(MutabilityException.class, () -> value.setNext(nftNumPair));
    }
}
