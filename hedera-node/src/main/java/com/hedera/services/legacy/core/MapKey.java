package com.hedera.services.legacy.core;

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

import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * @author Akshay
 * @Date : 9/13/2018
 */
public class MapKey implements FastCopyable {

	private static final long CURRENT_VERSION = 1;
	private static final long OBJECT_ID = 15486487;
	private long shardNum;
	private long realmNum;
	private long accountNum;

	public MapKey() {
	}

	public MapKey(final long shardNum, final long realmNum, final long accountNum) {
		this.shardNum = shardNum;
		this.realmNum = realmNum;
		this.accountNum = accountNum;
	}

	@SuppressWarnings("unchecked")
	public static <T extends FastCopyable> T deserialize(final DataInputStream inStream)
			throws IOException {
		MapKey key = new MapKey();

		deserialize(inStream, key);
		return (T) key;
	}

	private static void deserialize(final DataInputStream inStream, final MapKey key) throws IOException {
		final long version = inStream.readLong();
		final long objectId = inStream.readLong();

		key.realmNum = inStream.readLong();
		key.shardNum = inStream.readLong();
		key.accountNum = inStream.readLong();
	}

	public static MapKey getMapKey(final AccountID acctId) {
		return new MapKey(acctId.getShardNum(), acctId.getRealmNum(), acctId.getAccountNum());
	}

	public static MapKey getMapKey(final TopicID id) {
		return new MapKey(id.getShardNum(), id.getRealmNum(), id.getTopicNum());
	}

	public static MapKey getMapKey(final ContractID contractID) {
		return new MapKey(contractID.getShardNum(), contractID.getRealmNum(), contractID.getContractNum());
	}

	public long getShardNum() {
		return shardNum;
	}

	public void setShardNum(final long shardNum) {
		this.shardNum = shardNum;
	}

	public long getRealmNum() {
		return realmNum;
	}

	public void setRealmNum(final long realmNum) {
		this.realmNum = realmNum;
	}

	public long getAccountNum() {
		return accountNum;
	}

	public void setAccountNum(final long accountNum) {
		this.accountNum = accountNum;
	}

	public static MapKey getMapKey ( final JAccountID acctId){
		return new MapKey(acctId.getShardNum(), acctId.getRealmNum(), acctId.getAccountNum());
	}

	private void serialize(final DataOutputStream outStream) throws IOException {
		outStream.writeLong(CURRENT_VERSION);
		outStream.writeLong(OBJECT_ID);

		outStream.writeLong(this.realmNum);
		outStream.writeLong(this.shardNum);
		outStream.writeLong(this.accountNum);
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		MapKey mapKey = (MapKey) o;
		return shardNum == mapKey.shardNum &&
				realmNum == mapKey.realmNum &&
				accountNum == mapKey.accountNum;
	}

	@Override
	public int hashCode() {
		return Objects.hash(shardNum, realmNum, accountNum);
	}

	@Override
	public String toString() {
		return "MapKey{" +
				"shardNum=" + shardNum +
				", realmNum=" + realmNum +
				", accountNum=" + accountNum +
				'}';
	}

	@Override
	public FastCopyable copy() {
		return new MapKey(shardNum, realmNum, accountNum);
	}

	@Override
	public void copyTo(final FCDataOutputStream outStream) throws IOException {
		serialize(outStream);
	}

	@Override
	public void copyFrom(final FCDataInputStream inStream) throws IOException {
		//NoOp method
	}

	@Override
	public void copyToExtra(final FCDataOutputStream outStream) throws IOException {
		//NoOp method
	}

	@Override
	public void copyFromExtra(final FCDataInputStream inStream) throws IOException {
		//NoOp method
	}

	@Override
	public void diffCopyTo(final FCDataOutputStream outStream, final FCDataInputStream inStream) throws IOException {
		serialize(outStream);
	}

	@Override
	public void diffCopyFrom(final FCDataOutputStream outStream, final FCDataInputStream inStream) throws IOException {
		deserialize(inStream, this);
	}

	@Override
	public void delete() {
		//NoOp method
	}
}
