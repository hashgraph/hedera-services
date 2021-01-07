package com.hedera.services.state.submerkle;

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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
public class EntityIdTest {
	long shard = 1L, realm = 2L, num = 3L;

	SerializableDataInputStream in;
	SerializableDataOutputStream out;

	FileID fileId = FileID.newBuilder()
			.setShardNum(shard)
			.setRealmNum(realm)
			.setFileNum(num)
			.build();
	AccountID accountId = AccountID.newBuilder()
			.setShardNum(shard)
			.setRealmNum(realm)
			.setAccountNum(num)
			.build();
	ContractID contractId = ContractID.newBuilder()
			.setShardNum(shard)
			.setRealmNum(realm)
			.setContractNum(num)
			.build();
	TopicID topicId = TopicID.newBuilder()
			.setShardNum(shard)
			.setRealmNum(realm)
			.setTopicNum(num)
			.build();
	TokenID tokenId = TokenID.newBuilder()
			.setShardNum(shard)
			.setRealmNum(realm)
			.setTokenNum(num)
			.build();
	ScheduleID scheduleId = ScheduleID.newBuilder()
			.setShardNum(shard)
			.setRealmNum(realm)
			.setScheduleNum(num)
			.build();

	EntityId subject;

	@BeforeEach
	public void setup() {
		in = mock(SerializableDataInputStream.class);

		subject = new EntityId(shard, realm, num);
	}

	@Test
	public void objectContractWorks() {
		// given:
		var one = subject;
		var two = EntityId.MISSING_ENTITY_ID;
		var three = subject.copy();

		// expect:
		assertEquals(one, one);
		assertNotEquals(one, null);
		assertNotEquals(one, new Object());
		assertNotEquals(two, one);
		assertEquals(one, three);
		// and:
		assertEquals(one.hashCode(), three.hashCode());
		assertNotEquals(one.hashCode(), two.hashCode());
	}

	@Test
	public void toStringWorks() {
		// expect;
		assertEquals(
				"EntityId{shard=" + shard + ", realm=" + realm + ", num=" + num + "}",
				subject.toString());
	}

	@Test
	public void copyWorks() {
		// given:
		var copySubject = subject.copy();

		// then:
		assertFalse(subject == copySubject);
		assertEquals(subject, copySubject);
	}

	@Test
	public void gettersWork() {
		// expect:
		assertEquals(shard, subject.shard());
		assertEquals(realm, subject.realm());
		assertEquals(num, subject.num());
	}

	@Test
	public void factoriesWork() {
		// expect:
		assertNull(EntityId.ofNullableFileId(null));
		assertNull(EntityId.ofNullableAccountId(null));
		assertNull(EntityId.ofNullableContractId(null));
		assertNull(EntityId.ofNullableTopicId(null));
		assertNull(EntityId.ofNullableTokenId(null));
		assertNull(EntityId.ofNullableScheduleId(null));
		// and:
		assertEquals(subject, EntityId.ofNullableAccountId(accountId));
		assertEquals(subject, EntityId.ofNullableContractId(contractId));
		assertEquals(subject, EntityId.ofNullableTopicId(topicId));
		assertEquals(subject, EntityId.ofNullableFileId(fileId));
		assertEquals(subject, EntityId.ofNullableTokenId(tokenId));
		assertEquals(subject, EntityId.ofNullableScheduleId(scheduleId));
	}

	@Test
	public void legacyProviderWorks() throws IOException {
		given(in.readLong())
				.willReturn(5L)
				.willReturn(4L)
				.willReturn(shard)
				.willReturn(realm)
				.willReturn(num);

		// when:
		var readSubject = EntityId.LEGACY_PROVIDER.deserialize(in);

		// then:
		assertEquals(subject, readSubject);
	}

	@Test
	public void serializableDetWorks() {
		// expect;
		assertEquals(EntityId.MERKLE_VERSION, subject.getVersion());
		assertEquals(EntityId.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	@Test
	public void deserializeWorks() throws IOException {
		// setup:
		var newSubject = new EntityId();

		given(in.readLong())
				.willReturn(shard)
				.willReturn(realm)
				.willReturn(num);

		// when:
		newSubject.deserialize(in, EntityId.MERKLE_VERSION);

		// then:
		assertEquals(subject, newSubject);
	}

	@Test
	public void serializeWorks() throws IOException {
		// setup:
		out = mock(SerializableDataOutputStream.class);

		// when:
		subject.serialize(out);

		// then:
		verify(out).writeLong(shard);
		verify(out).writeLong(realm);
		verify(out).writeLong(num);
	}

	@Test
	public void viewsWork() {
		// expect:
		assertEquals(accountId, subject.toGrpcAccountId());
		assertEquals(contractId, subject.toGrpcContractId());
		assertEquals(tokenId, subject.toGrpcTokenId());
	}
}
