package com.hedera.services.context.domain.topic;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.context.domain.serdes.DomainSerdes;
import com.hedera.test.utils.ByteArrayConverter;
import com.hedera.test.utils.JAccountIDConverter;
import com.hedera.test.utils.JEd25519KeyConverter;
import com.hedera.test.utils.JTimestampConverter;
import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JTimestamp;
import com.swirlds.common.io.FCDataInputStream;
import org.apache.commons.codec.binary.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InvalidClassException;

import static com.hedera.services.context.domain.topic.TopicDeserializer.TOPIC_DESERIALIZER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class TopicDeserializerTest {
	FCDataInputStream in;
	DomainSerdes serdes;
	TopicDeserializer subject = TOPIC_DESERIALIZER;

	@BeforeEach
	private void setup() {
		in = mock(FCDataInputStream.class);
		serdes = mock(DomainSerdes.class);

		subject.serdes = serdes;
	}

	@AfterEach
	private void cleanup() {
		subject.serdes = new DomainSerdes();
	}

	@ParameterizedTest
	@CsvSource({
			"memo, 0000, 1111, 2, 3.4.5, 6666_777777777, true, 8, abcdef"
	})
	public void deserializeVersion1(String memo, @ConvertWith(JEd25519KeyConverter.class) JEd25519Key adminKey,
									@ConvertWith(JEd25519KeyConverter.class) JEd25519Key submitKey,
									long autoRenewDurationSeconds,
									@ConvertWith(JAccountIDConverter.class) JAccountID autoRenewAccountId,
									@ConvertWith(JTimestampConverter.class) JTimestamp expirationTimestamp,
									boolean deleted, long sequenceNumber,
									@ConvertWith(ByteArrayConverter.class) byte[] runningHash)
			throws Exception {
		setupV1MockAllValues(memo, adminKey, submitKey, autoRenewDurationSeconds, autoRenewAccountId,
				expirationTimestamp, deleted, sequenceNumber, runningHash);

		// given:
		Topic topic = subject.deserialize(in);

		// expect:
		assertTrue(topic.hasMemo());
		assertEquals(memo, topic.getMemo());
		assertTrue(topic.hasAdminKey());
		assertEquals(adminKey, topic.getAdminKey());
		assertTrue(topic.hasSubmitKey());
		assertEquals(submitKey, topic.getSubmitKey());
		assertEquals(autoRenewDurationSeconds, topic.getAutoRenewDurationSeconds());
		assertTrue(topic.hasAutoRenewAccountId());
		assertEquals(autoRenewAccountId, topic.getAutoRenewAccountId());
		assertTrue(topic.hasExpirationTimestamp());
		assertEquals(expirationTimestamp, topic.getExpirationTimestamp());
		assertEquals(deleted, topic.isDeleted());
		assertEquals(sequenceNumber, topic.getSequenceNumber());
		assertTrue(topic.hasRunningHash());
		assertEquals(runningHash, topic.getRunningHash());

		// and:
		verify(in, never()).readFully(any());
	}

	@Test
	public void deserializeVersion1DefaultEmptyValues() throws Exception {
		setupV1MockDefaultValues();

		// given:
		Topic topic = subject.deserialize(in);

		// expect:
		assertFalse(topic.hasMemo());
		assertEquals("", topic.getMemo());
		assertFalse(topic.hasAdminKey());
		assertTrue(topic.getAdminKey().isEmpty());
		assertFalse(topic.hasSubmitKey());
		assertTrue(topic.getSubmitKey().isEmpty());
		assertEquals(0L, topic.getAutoRenewDurationSeconds());
		assertFalse(topic.hasAutoRenewAccountId());
		assertEquals(new JAccountID(), topic.getAutoRenewAccountId());
		assertFalse(topic.hasExpirationTimestamp());
		assertEquals(new JTimestamp(), topic.getExpirationTimestamp());
		assertFalse(topic.isDeleted());
		assertEquals(0L, topic.getSequenceNumber());
		assertFalse(topic.hasRunningHash());
		assertArrayEquals(new byte[48], topic.getRunningHash());

		// and:
		verify(in, never()).readFully(any());
	}

	@Test
	public void deserializeInvalidMagicNumber() throws Exception {
		given(in.readShort())
				.willReturn((short)(TopicSerializer.OBJECT_ID - 1));

		// given:
		assertThrows(InvalidClassException.class, () -> subject.deserialize(in));
	}

	@Test
	public void deserializeInvalidVersion() throws Exception {
		given(in.readShort())
				.willReturn(TopicSerializer.OBJECT_ID)
				.willReturn((short)0); // Invalid object version

		// given:
		assertThrows(IOException.class, () -> subject.deserialize(in));
	}

	@Test
	public void deserializeInvalid() throws Exception {
		given(in.readShort())
				.willReturn(TopicSerializer.OBJECT_ID)
				.willReturn(TopicSerializer.CURRENT_VERSION);
		given(in.readBoolean())
				.willThrow(new IOException("no data"));

		// given:
		assertThrows(IOException.class, () -> subject.deserialize(in));
	}

	private void setupV1MockDefaultValues() throws Exception {
		given(in.readShort())
				.willReturn(TopicSerializer.OBJECT_ID)
				.willReturn((short)1);
		// memo, adminKey, submitKey, autoRenewDurationSeconds (long), autoRenewAccountId, expirationTimestamp,
		// deleted (bool), sequenceNumber (long), runningHash
		// All optional values are NOT written.
		given(in.readBoolean())
				.willReturn(false)
				.willReturn(false)
				.willReturn(false)
				.willReturn(false)
				.willReturn(false)
				.willReturn(false)
				.willReturn(false);
		given(in.readLong())
				.willReturn(0L)
				.willReturn(0L);
	}

	private void setupV1MockAllValues(String memo, JKey adminKey, JKey submitKey, long autoRenewDurationSeconds,
									  JAccountID autoRenewAccountId, JTimestamp expirationTimestamp, boolean deleted,
									  long sequenceNumber, byte[] runningHash) throws Exception {
		given(in.readShort())
				.willReturn(TopicSerializer.OBJECT_ID)
				.willReturn((short)1);
		// memo, adminKey, submitKey, autoRenewDurationSeconds (long), autoRenewAccountId, expirationTimestamp,
		// deleted (bool), sequenceNumber (long), runningHash
		given(in.readBoolean())
				.willReturn(true)
				.willReturn(true)
				.willReturn(true)
				.willReturn(true)
				.willReturn(true)
				.willReturn(deleted)
				.willReturn(true);
		given(serdes.deserializeKey(in))
				.willReturn(adminKey)
				.willReturn(submitKey);
		given(serdes.deserializeId(in))
				.willReturn(autoRenewAccountId);
		given(serdes.deserializeTimestamp(in))
				.willReturn(expirationTimestamp);
		given(in.readLong())
				.willReturn(autoRenewDurationSeconds)
				.willReturn(sequenceNumber);
		given(in.readBytes())
				.willReturn(StringUtils.getBytesUtf8(memo))
				.willReturn(runningHash);
	}
}
