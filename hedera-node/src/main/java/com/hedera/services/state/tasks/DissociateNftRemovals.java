package com.hedera.services.state.tasks;

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

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;

import java.io.IOException;

public class DissociateNftRemovals implements SelfSerializable {
	static final int RELEASE_0260_VERSION = 1;
	static final int CURRENT_VERSION = RELEASE_0260_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x88a07aa74cc10c3fL;

	private long accountNum;
	private long targetTokenNum;
	private long serialsCount;
	private long headNftTokenNum;
	private long headSerialNum;

	public DissociateNftRemovals() {

	}

	public DissociateNftRemovals(long accountNum, long targetTokenNum, long serialsCount, long headNftTokenNum, long headSerialNum) {
		this.accountNum = accountNum;
		this.targetTokenNum = targetTokenNum;
		this.serialsCount = serialsCount;
		this.headNftTokenNum = headNftTokenNum;
		this.headSerialNum = headSerialNum;
	}

	public long getAccountNum() {
		return accountNum;
	}

	public void setAccountNum(final long accountNum) {
		this.accountNum = accountNum;
	}

	public long getTargetTokenNum() {
		return targetTokenNum;
	}

	public void setTargetTokenNum(final long targetTokenNum) {
		this.targetTokenNum = targetTokenNum;
	}

	public long getSerialsCount() {
		return serialsCount;
	}

	public void setSerialsCount(final long serialsCount) {
		this.serialsCount = serialsCount;
	}

	public long getHeadNftTokenNum() {
		return headNftTokenNum;
	}

	public void setHeadNftTokenNum(final long headNftTokenNum) {
		this.headNftTokenNum = headNftTokenNum;
	}

	public long getHeadSerialNum() {
		return headSerialNum;
	}

	public void setHeadSerialNum(final long headSerialNum) {
		this.headSerialNum = headSerialNum;
	}

	@Override
	public void deserialize(final SerializableDataInputStream in, final int i) throws IOException {
		accountNum = in.readLong();
		targetTokenNum = in.readLong();
		serialsCount = in.readLong();
		headNftTokenNum = in.readLong();
		headSerialNum = in.readLong();
	}

	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeLong(accountNum);
		out.writeLong(targetTokenNum);
		out.writeLong(serialsCount);
		out.writeLong(headNftTokenNum);
		out.writeLong(headSerialNum);
	}

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return CURRENT_VERSION;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || DissociateNftRemovals.class != o.getClass()) {
			return false;
		}
		var that = (DissociateNftRemovals) o;
		return this.accountNum == that.accountNum &&
				this.targetTokenNum == that.targetTokenNum &&
				this.serialsCount == that.serialsCount &&
				this.headNftTokenNum == that.headNftTokenNum &&
				this.headSerialNum == that.headSerialNum;
	}
}
