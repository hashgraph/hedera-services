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

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;

public class EntityId implements SelfSerializable {
	private static final Logger log = LogManager.getLogger(EntityId.class);

	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xf35ba643324efa37L;

	public static final EntityId MISSING_ENTITY_ID = new EntityId(0, 0, 0);
	public static final EntityId.Provider LEGACY_PROVIDER = new Provider();

	private long shard;
	private long realm;
	private long num;

	@Deprecated
	public static class Provider {
		public EntityId deserialize(DataInputStream in) throws IOException {
			in.readLong();
			in.readLong();

			return new EntityId(in.readLong(), in.readLong(), in.readLong());
		}
	}

	public EntityId() { }

	public EntityId(long shard, long realm, long num) {
		this.shard = shard;
		this.realm = realm;
		this.num = num;
	}

	public EntityId(EntityId that) {
		this(that.shard, that.realm, that.num);
	}

	/* --- SelfSerializable --- */

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		shard = in.readLong();
		realm = in.readLong();
		num = in.readLong();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(shard);
		out.writeLong(realm);
		out.writeLong(num);
	}

	/* --- Object --- */

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || EntityId.class != o.getClass()) {
			return false;
		}
		EntityId that = (EntityId)o;
		return shard == that.shard && realm == that.realm && num == that.num;
	}

	@Override
	public int hashCode() {
		return Objects.hash(shard, realm, num);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("shard", shard)
				.add("realm", realm)
				.add("num", num)
				.toString();
	}

	public String toAbbrevString() {
		return String.format("%d.%d.%d", shard, realm, num);
	}

	public EntityId copy() {
		return new EntityId(this);
	}

	public long shard() {
		return shard;
	}

	public long realm() {
		return realm;
	}

	public long num() {
		return num;
	}

	/* --- Helpers --- */

	public static EntityId ofNullableAccountId(AccountID accountId) {
		return (accountId == null )
				? null
				: new EntityId(accountId.getShardNum(), accountId.getRealmNum(), accountId.getAccountNum());
	}

	public static EntityId ofNullableScheduleId(ScheduleID scheduleId) {
		return (scheduleId == null )
				? null
				: new EntityId(scheduleId.getShardNum(), scheduleId.getRealmNum(), scheduleId.getScheduleNum());
	}

	public static EntityId ofNullableFileId(FileID fileId) {
		return (fileId == null )
				? null
				: new EntityId(fileId.getShardNum(), fileId.getRealmNum(), fileId.getFileNum());
	}

	public static EntityId ofNullableTopicId(TopicID topicId) {
		return (topicId == null )
				? null
				: new EntityId(topicId.getShardNum(), topicId.getRealmNum(), topicId.getTopicNum());
	}

	public static EntityId ofNullableTokenId(TokenID tokenId) {
		return (tokenId == null )
				? null
				: new EntityId(tokenId.getShardNum(), tokenId.getRealmNum(), tokenId.getTokenNum());
	}

	public static EntityId ofNullableContractId(ContractID contractId) {
		return (contractId == null )
				? null
				: new EntityId(contractId.getShardNum(), contractId.getRealmNum(), contractId.getContractNum());
	}

	public ContractID toGrpcContractId() {
		return ContractID.newBuilder()
				.setShardNum(shard)
				.setRealmNum(realm)
				.setContractNum(num)
				.build();
	}

	public TokenID toGrpcTokenId() {
		return TokenID.newBuilder()
				.setShardNum(shard)
				.setRealmNum(realm)
				.setTokenNum(num)
				.build();
	}

	public AccountID toGrpcAccountId() {
		return AccountID.newBuilder()
				.setShardNum(shard)
				.setRealmNum(realm)
				.setAccountNum(num)
				.build();
	}
}
