/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.domain.topic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.test.utils.AccountIDConverter;
import com.hedera.test.utils.ByteArrayConverter;
import com.hedera.test.utils.EntityIdConverter;
import com.hedera.test.utils.InstantConverter;
import com.hedera.test.utils.JEd25519KeyConverter;
import com.hedera.test.utils.RichInstantConverter;
import com.hedera.test.utils.TopicIDConverter;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TopicID;
import java.io.IOException;
import java.time.Instant;
import org.apache.commons.codec.binary.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

class MerkleTopicTest {
    @Test
    void defaultConstructorTestingAccessors() {
        final var topic = new MerkleTopic();

        assertDefaultTopicAccessors(topic);
    }

    @Test
    void constructorWithNullsTestingAccessors() {
        final var topic = new MerkleTopic(null, null, null, 0L, null, null);

        assertDefaultTopicAccessors(topic);
    }

    @Test
    void constructorWithEmptyValuesTestingAccessors() {
        final var topic =
                new MerkleTopic(
                        "", new JKeyList(), new JKeyList(), 0L, new EntityId(), new RichInstant());

        assertDefaultTopicAccessors(topic);
    }

    @ParameterizedTest
    @CsvSource({
        "memo, 0000000000000000000000000000000000000000000000000000000000000000,"
                + " 1111111111111111111111111111111111111111111111111111111111111111, 2, 3.4.5,"
                + " 6666_777777777",
        "a, 12, 34, 5, 6.7.8, 1_0"
    })
    void constructorWithValuesTestingAccessors(
            final String memo,
            final @ConvertWith(JEd25519KeyConverter.class) JEd25519Key adminKey,
            final @ConvertWith(JEd25519KeyConverter.class) JEd25519Key submitKey,
            final long autoRenewDurationSeconds,
            final @ConvertWith(EntityIdConverter.class) EntityId autoRenewAccountId,
            final @ConvertWith(RichInstantConverter.class) RichInstant expirationTimestamp)
            throws IOException {
        final var topic =
                new MerkleTopic(
                        memo,
                        adminKey,
                        submitKey,
                        autoRenewDurationSeconds,
                        autoRenewAccountId,
                        expirationTimestamp);

        assertPostConstructorAccessors(
                topic,
                memo,
                adminKey,
                submitKey,
                autoRenewDurationSeconds,
                autoRenewAccountId,
                expirationTimestamp);
        assertEquals(0L, topic.getSequenceNumber());
        assertFalse(topic.hasRunningHash());
        assertArrayEquals(new byte[48], topic.getRunningHash());
    }

    @Test
    void copyConstructorWithDefaultsTestingAccessors() {
        final var topic = new MerkleTopic(new MerkleTopic());

        assertDefaultTopicAccessors(topic);
    }

    @Test
    void copyConstructorWithValuesTestingAccessors() throws IOException {
        final var memo = "memo";
        final var adminKey = new JEd25519Key(new byte[32]);
        final var submitKey = new JEd25519Key(new byte[32]);
        final var autoRenewDurationSeconds = 4L;
        final var autoRenewAccountId = new EntityId(1L, 2L, 3L);
        final var expirationTimestamp = new RichInstant(111L, 222);
        final var from =
                new MerkleTopic(
                        memo,
                        adminKey,
                        submitKey,
                        autoRenewDurationSeconds,
                        autoRenewAccountId,
                        expirationTimestamp);
        from.setRunningHash(new byte[48]);
        final var topic = new MerkleTopic(from);

        assertPostConstructorAccessors(
                topic,
                memo,
                adminKey,
                submitKey,
                autoRenewDurationSeconds,
                autoRenewAccountId,
                expirationTimestamp);
        assertEquals(0L, topic.getSequenceNumber());
        assertTrue(topic.hasRunningHash());
        assertArrayEquals(from.getRunningHash(), topic.getRunningHash());
        assertSame(from.getAdminKey(), topic.getAdminKey());
        assertSame(from.getSubmitKey(), topic.getSubmitKey());
        assertSame(from.getAutoRenewAccountId(), topic.getAutoRenewAccountId());
        assertSame(from.getExpirationTimestamp(), topic.getExpirationTimestamp());
        assertNotSame(from.getRunningHash(), topic.getRunningHash());
    }

    @Test
    void equalsDefault() {
        assertEquals(new MerkleTopic(), new MerkleTopic());
    }

    @Test
    void equalsNull() {
        final var equalsForcedCallResult = new MerkleTopic().equals(null);
        assertFalse(equalsForcedCallResult);
    }

    @Test
    void equalsWrongClass() {
        assertNotEquals(new MerkleTopic(), new Object());
    }

    @Test
    void equalsSame() {
        final var topic = new MerkleTopic();
        final var topicRef = topic;

        assertEquals(topic, topic);
        assertEquals(topic, topicRef);
    }

    @ParameterizedTest
    @CsvSource({
        "memo, 0000000000000000000000000000000000000000000000000000000000000000,"
                + " 1111111111111111111111111111111111111111111111111111111111111111, 2, 3.4.5,"
                + " 6666_777777777",
        ", , , 0, , 0_0"
    })
    void equalsViaCopy(
            final String memo,
            final @ConvertWith(JEd25519KeyConverter.class) JEd25519Key adminKey,
            final @ConvertWith(JEd25519KeyConverter.class) JEd25519Key submitKey,
            final long autoRenewDurationSeconds,
            final @ConvertWith(EntityIdConverter.class) EntityId autoRenewAccountId,
            final @ConvertWith(RichInstantConverter.class) RichInstant expirationTimestamp) {
        final var topic =
                new MerkleTopic(
                        memo,
                        adminKey,
                        submitKey,
                        autoRenewDurationSeconds,
                        autoRenewAccountId,
                        expirationTimestamp);

        assertEquals(topic, new MerkleTopic(topic));
    }

    // Set each attribute of 2 memos, differing by a single field and verify that they do not equal
    // each other.
    @ParameterizedTest
    @CsvSource({
        "memo, 0000, 1111, 2, 3.4.5, 6666_777777777, false, 0, 22, wrong-memo, 0000, 1111, 2,"
                + " 3.4.5, 6666_777777777, false, 0, 22",
        "memo, 0009, 1111, 2, 3.4.5, 6666_777777777, false, 0, 22, memo, 0000, 1111, 2, 3.4.5,"
                + " 6666_777777777, false, 0, 22",
        "memo, 0000, 1119, 2, 3.4.5, 6666_777777777, false, 0, 22, memo, 0000, 1111, 2, 3.4.5,"
                + " 6666_777777777, false, 0, 22",
        "memo, 0000, 1111, 9, 3.4.5, 6666_777777777, false, 0, 22, memo, 0000, 1111, 2, 3.4.5,"
                + " 6666_777777777, false, 0, 22",
        "memo, 0000, 1111, 2, 3.4.9, 6666_777777777, false, 0, 22, memo, 0000, 1111, 2, 3.4.5,"
                + " 6666_777777777, false, 0, 22",
        "memo, 0000, 1111, 2, 3.4.5, 6666_777777779, false, 0, 22, memo, 0000, 1111, 2, 3.4.5,"
                + " 6666_777777777, false, 0, 22",
        "memo, 0000, 1111, 2, 3.4.5, 6666_777777777, true, 0, 22, memo, 0000, 1111, 2, 3.4.5,"
                + " 6666_777777777, false, 0, 22",
        "memo, 0000, 1111, 2, 3.4.5, 6666_777777777, false, 9, 22, memo, 0000, 1111, 2, 3.4.5,"
                + " 6666_777777777, false, 0, 22",
        "memo, 0000, 1111, 2, 3.4.5, 6666_777777777, false, 0, 29, memo, 0000, 1111, 2, 3.4.5,"
                + " 6666_777777777, false, 0, 22"
    })
    void notEquals(
            final String aMemo,
            final @ConvertWith(JEd25519KeyConverter.class) JEd25519Key aAdminKey,
            final @ConvertWith(JEd25519KeyConverter.class) JEd25519Key aSubmitKey,
            final long aAutoRenewDurationSeconds,
            final @ConvertWith(EntityIdConverter.class) EntityId aAutoRenewAccountId,
            final @ConvertWith(RichInstantConverter.class) RichInstant aExpirationTimestamp,
            final boolean aDeleted,
            final long aSequenceNumber,
            final @ConvertWith(ByteArrayConverter.class) byte[] aRunningHash,
            final String bMemo,
            final @ConvertWith(JEd25519KeyConverter.class) JEd25519Key bAdminKey,
            final @ConvertWith(JEd25519KeyConverter.class) JEd25519Key bSubmitKey,
            final long bAutoRenewDurationSeconds,
            final @ConvertWith(EntityIdConverter.class) EntityId bAutoRenewAccountId,
            final @ConvertWith(RichInstantConverter.class) RichInstant bExpirationTimestamp,
            final boolean bDeleted,
            final long bSequenceNumber,
            final @ConvertWith(ByteArrayConverter.class) byte[] bRunningHash) {
        final var a =
                new MerkleTopic(
                        aMemo,
                        aAdminKey,
                        aSubmitKey,
                        aAutoRenewDurationSeconds,
                        aAutoRenewAccountId,
                        aExpirationTimestamp);
        a.setDeleted(aDeleted);
        a.setSequenceNumber(aSequenceNumber);
        a.setRunningHash(aRunningHash);
        final var b =
                new MerkleTopic(
                        bMemo,
                        bAdminKey,
                        bSubmitKey,
                        bAutoRenewDurationSeconds,
                        bAutoRenewAccountId,
                        bExpirationTimestamp);
        b.setDeleted(bDeleted);
        b.setSequenceNumber(bSequenceNumber);
        b.setRunningHash(bRunningHash);

        assertNotEquals(a, b);
    }

    @Test
    void hashIsSafeOnDefault() {
        assertDoesNotThrow(() -> new MerkleTopic().hashCode());
    }

    @ParameterizedTest
    @CsvSource({
        "memo, 0000000000000000000000000000000000000000000000000000000000000000,"
                + " 1111111111111111111111111111111111111111111111111111111111111111, 2, 3.4.5,"
                + " 6666_777777777",
        ", , , 55, , 1_234567890"
    })
    void hashCodeIsSafe(
            final String memo,
            final @ConvertWith(JEd25519KeyConverter.class) JEd25519Key adminKey,
            final @ConvertWith(JEd25519KeyConverter.class) JEd25519Key submitKey,
            final long autoRenewDurationSeconds,
            final @ConvertWith(EntityIdConverter.class) EntityId autoRenewAccountId,
            final @ConvertWith(RichInstantConverter.class) RichInstant expirationTimestamp) {
        assertDoesNotThrow(
                () ->
                        new MerkleTopic(
                                        memo,
                                        adminKey,
                                        submitKey,
                                        autoRenewDurationSeconds,
                                        autoRenewAccountId,
                                        expirationTimestamp)
                                .hashCode());
    }

    @Test
    void setRunningHashNull() {
        final var topic = new MerkleTopic();

        topic.setRunningHash(null);

        assertFalse(() -> topic.hasRunningHash());
    }

    @Test
    void setRunningHashEmpty() {
        final var topic = new MerkleTopic();

        topic.setRunningHash(new byte[0]);

        assertFalse(() -> topic.hasRunningHash());
    }

    @ParameterizedTest
    @CsvSource({
        "0, message, 4.5.6, 1.2.3, 0000, 1577401723000000000, "
            + "46d4ef0126b7be6c1c11ea854d5732a50aa0d7fb17b325c6977cec284c76814f969234d67c0b8cc414b9c84ff3e2bcb3",
        "1, '', 7.8.9, 0.0.0, , 1577401723987654321, "
            + "7547461ccf9bc8b598006f84c86bedee16967ba661da6f0c59c33476f8010932969b85aae59aff067e01e0fac6d45bda",
        "0, , 10.11.12, , , , "
            + "79200c525a751761dc25356d3dd01a34cf2a517e9c78e4b359ffd792f98f33f1ac1440dcd5e282abe73f6b265356218b"
    })
    void updateRunningHash(
            final long initialSequenceNumber,
            final String message,
            final @ConvertWith(AccountIDConverter.class) AccountID payer,
            final @ConvertWith(TopicIDConverter.class) TopicID topicId,
            final @ConvertWith(ByteArrayConverter.class) byte[] initialRunningHash,
            final @ConvertWith(InstantConverter.class) Instant consensusTimestampSeconds,
            final @ConvertWith(ByteArrayConverter.class) byte[] expectedRunningHash)
            throws IOException {
        final var topic = new MerkleTopic();
        topic.setSequenceNumber(initialSequenceNumber);
        topic.setRunningHash(initialRunningHash);
        topic.updateRunningHashAndSequenceNumber(
                payer, StringUtils.getBytesUtf8(message), topicId, consensusTimestampSeconds);

        assertEquals(initialSequenceNumber + 1, topic.getSequenceNumber());
        assertArrayEquals(expectedRunningHash, topic.getRunningHash());
    }

    /**
     * Assert that all the accessors for topic return default values.
     *
     * @param merkleTopic
     */
    private void assertDefaultTopicAccessors(final MerkleTopic merkleTopic) {
        assertFalse(merkleTopic.hasMemo());
        assertEquals("", merkleTopic.getMemo());
        assertFalse(merkleTopic.hasAdminKey());
        assertTrue(merkleTopic.getAdminKey().isEmpty());
        assertFalse(merkleTopic.hasSubmitKey());
        assertTrue(merkleTopic.getSubmitKey().isEmpty());
        assertEquals(0L, merkleTopic.getAutoRenewDurationSeconds());
        assertFalse(merkleTopic.hasAutoRenewAccountId());
        assertEquals(new EntityId(), merkleTopic.getAutoRenewAccountId());
        assertFalse(merkleTopic.hasExpirationTimestamp());
        assertEquals(new RichInstant(), merkleTopic.getExpirationTimestamp());
        assertFalse(merkleTopic.isDeleted());
        assertEquals(0L, merkleTopic.getSequenceNumber());
        assertFalse(merkleTopic.hasRunningHash());
        assertArrayEquals(new byte[48], merkleTopic.getRunningHash());
    }

    /**
     * Assert that topic has all values set (all the hasXyz methods return true) and the values
     * returned are those expected (input params).
     *
     * @param merkleTopic
     * @param memo
     * @param adminKey
     * @param submitKey
     * @param autoRenewDurationSeconds
     * @param autoRenewAccountId
     * @param expirationTimestamp
     * @throws IOException
     */
    private void assertPostConstructorAccessors(
            final MerkleTopic merkleTopic,
            final String memo,
            final JKey adminKey,
            final JKey submitKey,
            final long autoRenewDurationSeconds,
            final EntityId autoRenewAccountId,
            final RichInstant expirationTimestamp)
            throws IOException {
        assertTrue(merkleTopic.hasMemo());
        assertEquals(memo, merkleTopic.getMemo());

        // No good equality operator for JKey - compare serialize()d output.
        assertTrue(merkleTopic.hasAdminKey());
        assertArrayEquals(adminKey.serialize(), merkleTopic.getAdminKey().serialize());
        assertTrue(merkleTopic.hasSubmitKey());
        assertArrayEquals(submitKey.serialize(), merkleTopic.getSubmitKey().serialize());

        assertEquals(autoRenewDurationSeconds, merkleTopic.getAutoRenewDurationSeconds());
        assertTrue(merkleTopic.hasAutoRenewAccountId());
        assertEquals(autoRenewAccountId, merkleTopic.getAutoRenewAccountId());
        assertTrue(merkleTopic.hasExpirationTimestamp());
        assertEquals(expirationTimestamp, merkleTopic.getExpirationTimestamp());
        assertFalse(merkleTopic.isDeleted());
    }
}
