package com.hedera.services.sigs.metadata.lookups;

import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.utils.PermHashInteger;
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
	final PermHashInteger key = PermHashInteger.fromTopicId(target);

	@Mock
	private MerkleTopic topic;
	@Mock
	private MerkleMap<PermHashInteger, MerkleTopic> topics;

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

		Assertions.assertEquals(INVALID_TOPIC, result.failureIfAny()); }

	@Test
	void getsExtantSubmitKey() {
		given(topics.get(key)).willReturn(topic);
		given(topic.hasSubmitKey()).willReturn(true);
		given(topic.getSubmitKey()).willReturn(multi);

		final var result = subject.safeLookup(target);

		assertTrue(result.succeeded());
		assertSame(multi, result.metadata().getSubmitKey());
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
		assertSame(multi, result.metadata().getAdminKey());
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