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

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.fcqueue.FCQueueElement;

import java.io.IOException;

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
		if (taskType == SystemTaskType.DISSOCIATED_NFT_REMOVALS) {
			serializableTask = new DissociateNftRemovals(
					in.readLong(), in.readLong(), in.readLong(), in.readLong(), in.readLong());
		}
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

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || SystemTask.class != o.getClass()) {
			return false;
		}
		var that = (SystemTask) o;
		if (this.taskType != that.taskType) {
			return false;
		}
		if (this.taskType == SystemTaskType.DISSOCIATED_NFT_REMOVALS) {
			final var thisTask = (DissociateNftRemovals) this.serializableTask;
			final var thatTask = (DissociateNftRemovals) that.serializableTask;
			return thisTask.equals(thatTask);
		}
		return false;
	}
}
