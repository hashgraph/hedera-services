package com.hedera.services.state.merkle;

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

import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.serdes.TopicSerde;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MerkleTopicTest {
	private final int number = 123_456;

	String[] memos = new String[] {
			"First memo",
			"Second memo",
			"Third memo",
	};
	JKey[] adminKeys = new JKey[] {
			null,
			new JEd25519Key("abcdefghijklmnopqrstuvwxyz012345".getBytes()),
			new JKeyList(List.of(new JEd25519Key("ABCDEFGHIJKLMNOPQRSTUVWXYZ543210".getBytes())))
	};
	JKey[] submitKeys = new JKey[] {
			null,
			new JEd25519Key("aBcDeFgHiJkLmNoPqRsTuVwXyZ012345".getBytes()),
			new JKeyList(List.of(new JEd25519Key("AbCdEfGhIjKlMnOpQrStUvWxYz012345".getBytes())))
	};

	@Test
	void serializeWorks() throws IOException {
		// setup:
		final var topicSerde = mock(TopicSerde.class);
		final var out = mock(SerializableDataOutputStream.class);
		MerkleTopic.topicSerde = topicSerde;

		// given:
		final var subject = new MerkleTopic();
		subject.setKey(EntityNum.fromInt(number));

		// expect:
		assertEquals(number, subject.getKey().intValue());

		// and when:
		subject.serialize(out);

		// then:
		verify(topicSerde).serialize(subject, out);
		verify(out).writeInt(number);

		// cleanup:
		MerkleTopic.topicSerde = new TopicSerde();
	}

	@Test
	void deserializeWorksForPre0180() throws IOException {
		// setup:
		final var topicSerde = mock(TopicSerde.class);
		final var in = mock(SerializableDataInputStream.class);
		MerkleTopic.topicSerde = topicSerde;

		// given:
		final var subject = new MerkleTopic();

		// and when:
		subject.deserialize(in, MerkleTopic.PRE_RELEASE_0180_VERSION);

		// then:
		verify(topicSerde).deserializeV1(in, subject);

		// cleanup:
		MerkleTopic.topicSerde = new TopicSerde();
	}

	@Test
	void deserializeWorksFor0180() throws IOException {
		// setup:
		final var topicSerde = mock(TopicSerde.class);
		final var in = mock(SerializableDataInputStream.class);
		MerkleTopic.topicSerde = topicSerde;

		given(in.readInt()).willReturn(number);
		// and:
		final var subject = new MerkleTopic();

		// and when:
		subject.deserialize(in, MerkleTopic.RELEASE_0180_VERSION);

		// then:
		verify(topicSerde).deserializeV1(in, subject);
		// and:
		assertEquals(number, subject.getKey().intValue());

		// cleanup:
		MerkleTopic.topicSerde = new TopicSerde();
	}

	@Test
	void toStringWorks() throws IOException, NoSuchAlgorithmException {
		// expect:
		assertEquals(
				"MerkleTopic{number=0 <-> 0.0.0, "
						+ "memo=First memo, "
						+ "expiry=1234567.0, "
						+ "deleted=false, "
						+ "adminKey=<N/A>, "
						+ "submitKey=<N/A>, "
						+ "runningHash=<N/A>, "
						+ "sequenceNumber=0, "
						+ "autoRenewSecs=1234567, "
						+ "autoRenewAccount=1.2.3}",
				topicFrom(0).toString());
		// and:
		assertEquals(
				"MerkleTopic{number=1 <-> 0.0.1, " +
						"memo=Second memo, " +
						"expiry=2234567.1, " +
						"deleted=false, " +
						"adminKey=" + MiscUtils.describe(adminKeys[1]) + ", " +
						"submitKey=" + MiscUtils.describe(submitKeys[1]) + ", " +
						"runningHash" +
						"=<N/A>, " +
						"sequenceNumber=0, " +
						"autoRenewSecs=2234567, " +
						"autoRenewAccount=2.4.6}",
				topicFrom(1).toString());
		// and:
		assertEquals(
				"MerkleTopic{number=2 <-> 0.0.2, " +
						"memo=Third memo, " +
						"expiry=3234567.2, " +
						"deleted=false, " +
						"adminKey=" + MiscUtils.describe(adminKeys[2]) + ", " +
						"submitKey=" + MiscUtils.describe(submitKeys[2]) + ", " +
						"runningHash" +
						"=<N/A>, " +
						"sequenceNumber=0, " +
						"autoRenewSecs=3234567, " +
						"autoRenewAccount=3.6.9}",
				topicFrom(2).toString());
	}

	@Test
	void merkleMethodsWork() {
		final var topic = new MerkleTopic();
		assertEquals(MerkleTopic.CURRENT_VERSION, topic.getVersion());
		assertEquals(MerkleTopic.RUNTIME_CONSTRUCTABLE_ID, topic.getClassId());
	}

	private MerkleTopic topicFrom(int s) {
		long v = 1_234_567L + s * 1_000_000L;
		long t = s + 1;
		var topic = new MerkleTopic(
				memos[s],
				adminKeys[s],
				submitKeys[s],
				v,
				new EntityId(t, t * 2, t * 3),
				new RichInstant(v, s));
		topic.setKey(EntityNum.fromInt(s));
		return topic;
	}

}
