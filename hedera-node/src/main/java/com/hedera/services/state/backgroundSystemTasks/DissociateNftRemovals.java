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
	long tokenNum;
	long serialsCount;

	public DissociateNftRemovals(long accountNum, long tokenNum, long serialsCount) {
		this.accountNum = accountNum;
		this.tokenNum = tokenNum;
		this.serialsCount = serialsCount;
	}

	@Override
	public void deserialize(final SerializableDataInputStream in, final int i) throws IOException {
		accountNum = in.readLong();
		tokenNum = in.readLong();
		serialsCount = in.readLong();
	}

	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeLong(accountNum);
		out.writeLong(tokenNum);
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
