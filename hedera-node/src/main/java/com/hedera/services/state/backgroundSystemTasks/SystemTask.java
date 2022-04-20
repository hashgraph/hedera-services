package com.hedera.services.state.backgroundSystemTasks;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.fcqueue.FCQueueElement;

import java.io.IOException;

import static com.hedera.services.state.serdes.IoUtils.readNullableSerializable;

public class SystemTask implements FCQueueElement {
	static final int RELEASE_0260_VERSION = 1;
	static final int CURRENT_VERSION = RELEASE_0260_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x37425e113a62eb3dL;

	private Hash hash;
	private SystemTaskType taskType;
	private SelfSerializable serializableTask;

	public SystemTask() {
		/* RuntimeConstructable */
	}

	public SystemTask(SystemTaskType taskType, SelfSerializable task) {
		this.taskType = taskType;
		this.serializableTask = task;
	}

	public SystemTaskType getTaskType() {
		return taskType;
	}

	public void setTaskType(final SystemTaskType taskType) {
		this.taskType = taskType;
	}

	public SelfSerializable getSerializableTask() {
		return serializableTask;
	}

	public void setSerializableTask(final SelfSerializable serializableTask) {
		this.serializableTask = serializableTask;
	}

	@Override
	public SystemTask copy() {
		return this;
	}

	@Override
	public void release() {
		/* No-op */
	}

	@Override
	public Hash getHash() {
		return hash;
	}

	@Override
	public void setHash(final Hash hash) {
		this.hash = hash;
	}

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		taskType = SystemTaskType.get(in.readInt());
		serializableTask = readNullableSerializable(in);
	}

	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeInt(taskType.ordinal());
		serializableTask.serialize(out);
	}

	@Override
	public int getVersion() {
		return CURRENT_VERSION;
	}
}
