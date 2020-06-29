package com.hedera.services.state.serdes;

import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.RichInstant;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.BDDMockito.*;

class MerkleTopicSerdeTest {
	long autoRenewSecs = 1_234_567L;
	long seqNo = 7_654_321L;
	byte[] hash = new byte[MerkleTopic.RUNNING_HASH_BYTE_ARRAY_SIZE];
	JKey adminKey, submitKey;
	String memo = "Anything";
	EntityId autoRenewId;
	RichInstant expiry;

	DomainSerdes serdes;

	TopicSerde subject;

	@BeforeEach
	public void setup() {
		adminKey = mock(JKey.class);
		submitKey = mock(JKey.class);
		serdes = mock(DomainSerdes.class);
		autoRenewId = mock(EntityId.class);
		given(autoRenewId.num()).willReturn(13257L);
		expiry = mock(RichInstant.class);
		given(expiry.getSeconds()).willReturn(1_234_567L);
		TopicSerde.serdes = serdes;

		subject = new TopicSerde();
	}

	@AfterEach
	public void cleanup() {
		TopicSerde.serdes = new DomainSerdes();
	}

	@Test
	public void serializesFullCurrentAsExpected() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		var topic = mock(MerkleTopic.class);
		// and:
		InOrder inOrder = inOrder(out, serdes);

		// given:
		withBasics(topic);
		withMemo(topic);
		withExpiry(topic);
		withAdminKey(topic);
		withSubmitKey(topic);
		withAutoRenewId(topic);
		withHash(topic);

		// when:
		subject.serialize(topic, out);

		// then:
		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(out).writeBytes(memo);
		// and:
		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(serdes).serializeKey(adminKey, out);
		// and:
		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(serdes).serializeKey(submitKey, out);
		// and:
		inOrder.verify(out).writeLong(autoRenewSecs);
		// and:
		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(serdes).serializeId(autoRenewId, out);
		// and:
		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(serdes).serializeTimestamp(expiry, out);
		// and:
		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(out).writeLong(seqNo);
		// and:
		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(out).writeByteArray(hash);
	}

	@Test
	public void serializesCurrentBasicsAsExpected() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		var topic = mock(MerkleTopic.class);
		// and:
		InOrder inOrder = inOrder(out, serdes);

		// given:
		withBasics(topic);

		// when:
		subject.serialize(topic, out);

		// then:
		inOrder.verify(out, times(3)).writeBoolean(false);
		// and:
		inOrder.verify(out).writeLong(autoRenewSecs);
		// and:
		inOrder.verify(out, times(2)).writeBoolean(false);
		// and:
		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(out).writeLong(seqNo);
		// and:
		inOrder.verify(out).writeBoolean(false);
	}

	@Test
	public void deserializesMinimalV1() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var topic = new MerkleTopic();

		// given:
		configureMinimal(in, serdes);

		// when:
		subject.deserializeV1(in, topic);

		// then:
		assertFalse(topic.hasMemo());
		assertFalse(topic.hasAdminKey());
		assertFalse(topic.hasSubmitKey());
		assertFalse(topic.hasAutoRenewAccountId());
		assertFalse(topic.hasExpirationTimestamp());
		assertFalse(topic.isDeleted());
		assertFalse(topic.hasRunningHash());
		// and:
		assertEquals(topic.getSequenceNumber(), seqNo);
		assertEquals(topic.getAutoRenewDurationSeconds(), autoRenewSecs);
	}

	@Test
	public void deserializesFullV1() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var topic = new MerkleTopic();

		// given:
		configureFull(in, serdes);

		// when:
		subject.deserializeV1(in, topic);

		// then:
		assertTrue(topic.hasMemo());
		assertTrue(topic.hasAdminKey());
		assertTrue(topic.hasSubmitKey());
		assertTrue(topic.hasAutoRenewAccountId());
		assertTrue(topic.hasExpirationTimestamp());
		assertTrue(topic.isDeleted());
		assertTrue(topic.hasRunningHash());
		// and:
		assertEquals(topic.getMemo(), memo);
		assertEquals(topic.getAdminKey(), adminKey);
		assertEquals(topic.getSubmitKey(), submitKey);
		assertEquals(topic.getAutoRenewAccountId(), autoRenewId);
		assertEquals(topic.getExpirationTimestamp(), expiry);
		assertEquals(topic.getRunningHash(), hash);
		assertEquals(topic.getSequenceNumber(), seqNo);
		assertEquals(topic.getAutoRenewDurationSeconds(), autoRenewSecs);
	}

	private void configureFull(SerializableDataInputStream in, DomainSerdes serdes) throws IOException {
		given(in.readBoolean())
				.willReturn(true);
		given(in.readByteArray(TopicSerde.MAX_MEMO_BYTES))
				.willReturn(memo.getBytes())
				.willReturn(null);
		given(in.readByteArray(MerkleTopic.RUNNING_HASH_BYTE_ARRAY_SIZE))
				.willReturn(hash);
		given(serdes.deserializeKey(in))
				.willReturn(adminKey)
				.willReturn(submitKey);
		given(in.readLong())
				.willReturn(autoRenewSecs)
				.willReturn(seqNo);
		given(serdes.deserializeId(in))
				.willReturn(autoRenewId);
		given(serdes.deserializeTimestamp(in))
				.willReturn(expiry);
	}

	private void configureMinimal(SerializableDataInputStream in, DomainSerdes serdes) throws IOException {
		given(in.readLong())
				.willReturn(autoRenewSecs)
				.willReturn(seqNo);
		given(serdes.deserializeId(in))
				.willReturn(autoRenewId);
	}

	private void withBasics(MerkleTopic t) {
		given(t.getAutoRenewDurationSeconds()).willReturn(autoRenewSecs);
		given(t.isDeleted()).willReturn(true);
		given(t.getSequenceNumber()).willReturn(seqNo);
	}

	private void withMemo(MerkleTopic t) {
		given(t.hasMemo()).willReturn(true);
		given(t.getMemo()).willReturn(memo);
	}

	private void withAdminKey(MerkleTopic t) {
		given(t.hasAdminKey()).willReturn(true);
		given(t.getAdminKey()).willReturn(adminKey);
	}

	private void withSubmitKey(MerkleTopic t) {
		given(t.hasSubmitKey()).willReturn(true);
		given(t.getSubmitKey()).willReturn(submitKey);
	}

	private void withAutoRenewId(MerkleTopic t) {
		given(t.hasAutoRenewAccountId()).willReturn(true);
		given(t.getAutoRenewAccountId()).willReturn(autoRenewId);
	}

	private void withExpiry(MerkleTopic t) {
		given(t.hasExpirationTimestamp()).willReturn(true);
		given(t.getExpirationTimestamp()).willReturn(expiry);
	}

	private void withHash(MerkleTopic t) {
		given(t.hasRunningHash()).willReturn(true);
		given(t.getRunningHash()).willReturn(hash);
	}
}