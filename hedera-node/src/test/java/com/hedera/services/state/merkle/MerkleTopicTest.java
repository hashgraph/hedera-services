package com.hedera.services.state.merkle;

import com.hedera.services.context.domain.topic.LegacyTopicsTest;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.serdes.TopicSerde;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.io.SerializableDataInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class MerkleTopicTest {
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
	public void legacyProviderWorksWithFullTopic() throws Exception {
		// setup:
		var serde = mock(TopicSerde.class);
		var serdes = mock(DomainSerdes.class);
		var legacyIdProvider = mock(EntityId.Provider.class);
		var in = mock(SerializableDataInputStream.class);
		// and:
		MerkleTopic.serdes = serdes;
		MerkleTopic.legacyIdProvider = legacyIdProvider;
		MerkleTopic.topicSerde = serde;
		// and:
		var expected = LegacyTopicsTest.topicFrom(1);

		given(in.readShort()).willReturn((short)-1)
				.willReturn((short)-2);
		given(in.readBoolean())
				.willReturn(true)
				.willReturn(true)
				.willReturn(true)
				.willReturn(true)
				.willReturn(true)
				.willReturn(false)
				.willReturn(true);
		given(in.readByteArray(MerkleTopic.MAX_MEMO_BYTES))
				.willReturn(expected.getMemo().getBytes());
		given(in.readByteArray(MerkleTopic.RUNNING_HASH_BYTE_ARRAY_SIZE))
				.willReturn(expected.getRunningHash());
		given(serdes.deserializeLegacyTimestamp(in))
				.willReturn(expected.getExpirationTimestamp());
		given(serdes.deserializeKey(in))
				.willReturn(expected.getAdminKey())
				.willReturn(expected.getSubmitKey());
		given(in.readLong())
				.willReturn(expected.getAutoRenewDurationSeconds())
				.willReturn(expected.getSequenceNumber());
		given(legacyIdProvider.deserialize(in))
				.willReturn(expected.getAutoRenewAccountId());

		// when:
		var topic = (MerkleTopic)(MerkleTopic.LEGACY_PROVIDER.deserialize(in));

		// then:
		assertEquals(expected, topic);
	}

	@AfterEach
	public void cleanup() {
		MerkleTopic.topicSerde = new TopicSerde();
		MerkleTopic.serdes = new DomainSerdes();
		MerkleTopic.legacyIdProvider = EntityId.LEGACY_PROVIDER;
	}

	@Test
	public void toStringWorks() throws IOException, NoSuchAlgorithmException {
		// expect:
		assertEquals(
				"MerkleTopic{"
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
				"MerkleTopic{" +
						"memo=Second memo, " +
						"expiry=2234567.1, " +
						"deleted=false, " +
						"adminKey=" + MiscUtils.describe(adminKeys[1]) + ", " +
						"submitKey=" + MiscUtils.describe(submitKeys[1]) + ", " +
						"runningHash=3c8e1604b2cd20068f02976fa10217491561cc864b7bff28451e1f1a0a8c58c02df56f60562f129e845e0ba16e3420eb, " +
						"sequenceNumber=1, " +
						"autoRenewSecs=2234567, " +
						"autoRenewAccount=2.4.6}",
				topicFrom(1).toString());
		// and:
		assertEquals(
				"MerkleTopic{" +
						"memo=Third memo, " +
						"expiry=3234567.2, " +
						"deleted=false, " +
						"adminKey=" + MiscUtils.describe(adminKeys[2]) + ", " +
						"submitKey=" + MiscUtils.describe(submitKeys[2]) + ", " +
						"runningHash=a19f77d351424204e3eeec1bb42bcdc728e521483bb99103dc7fa7c527db0c14aeefe4b0a8a7d0924b2f2c4a1d237bc5, " +
						"sequenceNumber=2, " +
						"autoRenewSecs=3234567, " +
						"autoRenewAccount=3.6.9}",
				topicFrom(2).toString());
	}

	private MerkleTopic topicFrom(int s) throws IOException, NoSuchAlgorithmException {
		long v = 1_234_567L + s * 1_000_000L;
		long t = s + 1;
		AccountID payer = AccountID.newBuilder().setAccountNum(123).build();
		TopicID id = TopicID.newBuilder().setTopicNum(s).build();
		var topic = new MerkleTopic(
				memos[s],
				adminKeys[s],
				submitKeys[s],
				v,
				new EntityId(t, t * 2, t * 3),
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
}