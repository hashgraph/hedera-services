package com.hedera.services.sigs.metadata.lookups;

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
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static com.hedera.services.sigs.order.KeyOrderingFailure.INVALID_TOPIC;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DefaultTopicLookupTest {
	final JKey multi = new JEd25519Key("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes(StandardCharsets.UTF_8));
	final TopicID target = IdUtils.asTopic("0.0.1234");
	final EntityNum key = EntityNum.fromTopicId(target);

	@Mock
	private MerkleTopic topic;
	@Mock
	private MerkleMap<EntityNum, MerkleTopic> topics;

	private DefaultTopicLookup subject;

	@BeforeEach
	void setUp() {
		subject = new DefaultTopicLookup(() -> topics);
	}

	@Test
	void failsOnMissingTopic() {
		final var result = subject.safeLookup(target);

		Assertions.assertEquals(INVALID_TOPIC, result.failureIfAny());
	}

	@Test
	void failsOnDeletedTopic() {
		given(topics.get(key)).willReturn(topic);
		given(topic.isDeleted()).willReturn(true);

		final var result = subject.safeLookup(target);

		Assertions.assertEquals(INVALID_TOPIC, result.failureIfAny());
	}

	@Test
	void getsExtantSubmitKey() {
		given(topics.get(key)).willReturn(topic);
		given(topic.hasSubmitKey()).willReturn(true);
		given(topic.getSubmitKey()).willReturn(multi);

		final var result = subject.safeLookup(target);

		assertTrue(result.succeeded());
		assertSame(multi, result.metadata().submitKey());
	}

	@Test
	void getsMissingSubmitKey() {
		given(topics.get(key)).willReturn(topic);
		given(topic.hasSubmitKey()).willReturn(false);

		final var result = subject.safeLookup(target);

		assertTrue(result.succeeded());
		assertFalse(result.metadata().hasSubmitKey());
	}

	@Test
	void getsExtantAdminKey() {
		given(topics.get(key)).willReturn(topic);
		given(topic.hasAdminKey()).willReturn(true);
		given(topic.getAdminKey()).willReturn(multi);

		final var result = subject.safeLookup(target);

		assertTrue(result.succeeded());
		assertSame(multi, result.metadata().adminKey());
	}

	@Test
	void getsMissingAdminKey() {
		given(topics.get(key)).willReturn(topic);
		given(topic.hasAdminKey()).willReturn(false);

		final var result = subject.safeLookup(target);

		assertTrue(result.succeeded());
		assertFalse(result.metadata().hasAdminKey());
	}
}
