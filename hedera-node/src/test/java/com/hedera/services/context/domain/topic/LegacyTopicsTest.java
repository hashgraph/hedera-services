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

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.submerkle.RichInstant;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@RunWith(JUnitPlatform.class)
public class LegacyTopicsTest {
	@Test
	public void readFcMap() throws IOException, NoSuchAlgorithmException {
		// setup:
/*
		FCMap<MapKey, Topic> subject = new FCMap<>(MapKey::deserialize, Topic::deserialize);

		for (int s = 0; s < 3; s++) {
			subject.put(keyFrom(s), topicFrom(s));
		}

		var out = new FCDataOutputStream(...);
		subject.copyTo(out);
		subject.copyToExtra(out);
*/

		// given:
		FCMap<MerkleEntityId, MerkleTopic> subject =
				new FCMap<>(new MerkleEntityId.Provider(), MerkleTopic.LEGACY_PROVIDER);
		// and:
		var in = new SerializableDataInputStream(
				Files.newInputStream(Paths.get("src/test/resources/testTopics.fcm")));

		// when:
		subject.copyFrom(in);
		subject.copyFromExtra(in);

		// then:
		assertEquals(subject.size(), N);
		for (int s = 0; s < N; s++) {
			var id = idFrom(s);
			assertTrue(subject.containsKey(id));
			var actual = subject.get(id);
			var expected = topicFrom(s);

			System.out.println("--- Expected ---");
			System.out.println(expected.toString());
			System.out.println("--- Actual ---");
			System.out.println(actual.toString());

//			assertEquals(expected, actual);
		}
	}

	static int N = 3;
	static String[] memos = new String[] {
			"First memo",
			"Second memo",
			"Third memo",
	};
	static JKey[] adminKeys = new JKey[] {
			null,
			new JEd25519Key("abcdefghijklmnopqrstuvwxyz012345".getBytes()),
			new JKeyList(List.of(new JEd25519Key("ABCDEFGHIJKLMNOPQRSTUVWXYZ543210".getBytes())))
	};
	static JKey[] submitKeys = new JKey[] {
			null,
			new JEd25519Key("aBcDeFgHiJkLmNoPqRsTuVwXyZ012345".getBytes()),
			new JKeyList(List.of(new JEd25519Key("AbCdEfGhIjKlMnOpQrStUvWxYz012345".getBytes())))
	};

	public static MerkleTopic topicFrom(int s) throws IOException, NoSuchAlgorithmException {
		long v = 1_234_567L + s * 1_000_000L;
		AccountID payer = AccountID.newBuilder().setAccountNum(123).build();
		TopicID id = TopicID.newBuilder().setTopicNum(s).build();
		var topic = new MerkleTopic(
				memos[s],
				adminKeys[s],
				submitKeys[s],
				v,
				new EntityId(s, s, s),
				new RichInstant(v, s));
		for (int i = 0; i < s; i++) {
			topic.updateRunningHashAndSequenceNumber(
					payer,
					"Hello world!".getBytes(),
					id,
					Instant.ofEpochSecond(v, i));
		}
		return topic;
	}

	private MerkleEntityId idFrom(long s) {
		long t = s + 1;
		return new MerkleEntityId(t, 2 * t, 3 * t);
	}
}
