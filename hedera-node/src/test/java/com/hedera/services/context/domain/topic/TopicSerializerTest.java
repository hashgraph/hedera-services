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
import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JTimestamp;
import com.swirlds.common.io.FCDataOutputStream;
import org.apache.commons.codec.binary.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.internal.verification.VerificationModeFactory;

import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class TopicSerializerTest {
	FCDataOutputStream out;
	DomainSerdes serdes;
	TopicSerializer subject = TopicSerializer.TOPIC_SERIALIZER;

	@BeforeEach
	private void setup() {
		out = mock(FCDataOutputStream.class);
		serdes = mock(DomainSerdes.class);

		subject.serdes = serdes;
	}

	@AfterEach
	private void cleanup() {
		subject.serdes = new DomainSerdes();
	}

	@Test
	public void serializeDefault() throws Exception {
		InOrder inOrder = inOrder(out);

		// given:
		Topic topic = new Topic();

		// when:
		subject.serialize(topic, out);

		// then:
		inOrder.verify(out).writeShort(TopicSerializer.OBJECT_ID);
		inOrder.verify(out).writeShort(TopicSerializer.CURRENT_VERSION);
		inOrder.verify(out, VerificationModeFactory.times(3)).writeBoolean(false); // memo, adminKey, submitKey
		inOrder.verify(out).writeLong(0L); // autoRenewDurationSeconds
		inOrder.verify(out, VerificationModeFactory.times(3)).writeBoolean(false); // autoRenewAccountId, expirationTimestamp, deleted
		inOrder.verify(out).writeLong(0L); // sequenceNumber
		inOrder.verify(out).writeBoolean(false); // runningHash
	}

	@Test
	public void serializeWithValues() throws Exception {
		InOrder inOrder = inOrder(out, serdes);

		// given:
		var memo = "memo";
		var adminKey = new JEd25519Key(new byte[2]);
		var submitKey = new JEd25519Key(new byte[3]);
		var autoRenewDurationSeconds = 44L;
		var autoRenewAccountId = new JAccountID(5L, 6L, 7L);
		var expirationTimestamp = new JTimestamp(88L, 99);
		var sequenceNumber = 10L;
		var runningHash = new byte[11];
		Topic topic = new Topic(memo, adminKey, submitKey, autoRenewDurationSeconds, autoRenewAccountId,
				expirationTimestamp);
		topic.setSequenceNumber(sequenceNumber);
		topic.setRunningHash(runningHash);

		// when:
		subject.serialize(topic, out);

		// then:
		inOrder.verify(out).writeShort(TopicSerializer.OBJECT_ID);
		inOrder.verify(out).writeShort(TopicSerializer.CURRENT_VERSION);
		inOrder.verify(out).writeBoolean(true);
		var memoBytes = StringUtils.getBytesUtf8(memo);
		inOrder.verify(out).writeBytes(memoBytes);
		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(serdes).serializeKey(adminKey, out);
		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(serdes).serializeKey(submitKey, out);
		inOrder.verify(out).writeLong(autoRenewDurationSeconds);
		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(serdes).serializeId(autoRenewAccountId, out);
		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(serdes).serializeTimestamp(expirationTimestamp, out);
		inOrder.verify(out).writeBoolean(false); // deleted
		inOrder.verify(out).writeLong(sequenceNumber);
		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(out).writeBytes(runningHash);
	}
}
