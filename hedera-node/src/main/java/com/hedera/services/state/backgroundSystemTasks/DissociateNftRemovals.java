package com.hedera.services.state.backgroundSystemTasks;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;

import java.io.IOException;

public class DissociateNftRemovals implements SelfSerializable {
	static final int RELEASE_0260_VERSION = 1;
	static final int CURRENT_VERSION = RELEASE_0260_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x88a07aa74cc10c3fL;

	long accountNum;
	long targetTokenNum;
	long serialsCount;
	long headTokenNum;
	long headSerialNum;

	public DissociateNftRemovals(long accountNum, long targetTokenNum, long serialsCount, long headTokenNum, long headSerialNum) {
		this.accountNum = accountNum;
		this.targetTokenNum = targetTokenNum;
		this.serialsCount = serialsCount;
		this.headTokenNum = headTokenNum;
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

	public long getHeadTokenNum() {
		return headTokenNum;
	}

	public void setHeadTokenNum(final long headTokenNum) {
		this.headTokenNum = headTokenNum;
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
	}

	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeLong(accountNum);
		out.writeLong(targetTokenNum);
		out.writeLong(serialsCount);
	}

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return CURRENT_VERSION;
	}
}
