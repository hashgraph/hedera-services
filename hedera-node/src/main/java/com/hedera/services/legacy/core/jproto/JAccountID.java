package com.hedera.services.legacy.core.jproto;

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
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.builder.RequestBuilder;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;

/**
 * Java class maps to proto class AccountID.
 *
 * @author Hua Li Created on 2018-11-05
 */
public class JAccountID implements FastCopyable, Serializable {
	private static final Logger log = LogManager.getLogger(JAccountID.class);

	private static final long serialVersionUID = 1L;
	private static final long LEGACY_VERSION_1 = 1;
	private static final long CURRENT_VERSION = 2;
	private long shardNum;
	private long realmNum;
	private long accountNum;

	public JAccountID() {
	}

	public JAccountID(long shardNum, long realmNum, long accountNum) {
		super();
		this.shardNum = shardNum;
		this.realmNum = realmNum;
		this.accountNum = accountNum;
	}

	public JAccountID(final JAccountID other) {
		super();
		this.shardNum = other.shardNum;
		this.realmNum = other.realmNum;
		this.accountNum = other.accountNum;
	}

	public static JAccountID convert(AccountID accID) {
		if (accID == null) {
			return null;
		}
		return new JAccountID(accID.getShardNum(), accID.getRealmNum(), accID.getAccountNum());
	}

	public static JAccountID clone(JAccountID accID) {
		if (accID == null) {
			return null;
		}
		return new JAccountID(accID.getShardNum(), accID.getRealmNum(), accID.getAccountNum());
	}

	public static JAccountID convert(ContractID contractID) {
		if (contractID == null) {
			return null;
		}
		return new JAccountID(contractID.getShardNum(), contractID.getRealmNum(),
				contractID.getContractNum());
	}

	public static ContractID convert(JAccountID jAccountID) {
		return RequestBuilder.getContractIdBuild(
				jAccountID.getAccountNum(),
				jAccountID.getRealmNum(),
				jAccountID.getShardNum());
	}

	public static JAccountID convert(FileID fileID) {
		if (fileID == null) {
			return null;
		}
		return new JAccountID(fileID.getShardNum(), fileID.getRealmNum(), fileID.getFileNum());
	}

	public static JAccountID convert(@Nullable TopicID topicId) {
		if (topicId == null) {
			return null;
		}
		return new JAccountID(topicId.getShardNum(), topicId.getRealmNum(), topicId.getTopicNum());
	}

	@Override
	public String toString() {
		return "<JAccountID: " + shardNum + "." + realmNum + "." + accountNum + ">";
	}

	public long getShardNum() {
		return shardNum;
	}

	public void setShardNum(long shardNum) {
		this.shardNum = shardNum;
	}

	public long getRealmNum() {
		return realmNum;
	}

	public void setRealmNum(long realmNum) {
		this.realmNum = realmNum;
	}

	public long getAccountNum() {
		return accountNum;
	}

	public void setAccountNum(long accountNum) {
		this.accountNum = accountNum;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		JAccountID that = (JAccountID) o;
		return shardNum == that.shardNum &&
				realmNum == that.realmNum &&
				accountNum == that.accountNum;
	}

	@Override
	public int hashCode() {
		return Objects.hash(shardNum, realmNum, accountNum);
	}

	private void serialize(final DataOutputStream outStream) throws IOException {

		outStream.writeLong(CURRENT_VERSION);
		outStream.writeLong(JObjectType.JAccountID.longValue());
		outStream.writeLong(this.shardNum);
		outStream.writeLong(this.realmNum);
		outStream.writeLong(this.accountNum);

	}

	@SuppressWarnings("unchecked")
	public static <T extends FastCopyable> T deserialize(final DataInputStream inStream) throws IOException {
		final JAccountID jAccountID = new JAccountID();

		deserialize(inStream, jAccountID);
		return (T) jAccountID;
	}

	private static void deserialize(final DataInputStream inStream, final JAccountID jAccountID) throws IOException {
		long version = inStream.readLong();
		if (version < LEGACY_VERSION_1 || version > CURRENT_VERSION) {
			throw new IllegalStateException("Illegal version was read from the stream");
		}

		long objectType = inStream.readLong();
		JObjectType type = JObjectType.valueOf(objectType);
		if (!JObjectType.JAccountID.equals(type)) {
			throw new IllegalStateException("Illegal JObjectType was read from the stream");
		}

		jAccountID.shardNum = inStream.readLong();
		jAccountID.realmNum = inStream.readLong();
		jAccountID.accountNum = inStream.readLong();
	}

	@Override
	public FastCopyable copy() {
		return new JAccountID(this);
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
