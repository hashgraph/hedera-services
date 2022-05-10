package com.hedera.services.state.merkle;

/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.utils.EntityNum;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;

import java.io.IOException;

public class MerkleStakingInfo extends AbstractMerkleLeaf implements Keyed<EntityNum> {
	static final int RELEASE_0270_VERSION = 1;

	static final int CURRENT_VERSION = RELEASE_0270_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xb8b383ccd3caed5bL;

	@Override
	public AbstractMerkleLeaf copy() {
		return null;
	}

	@Override
	public void deserialize(final SerializableDataInputStream serializableDataInputStream,
			final int i) throws IOException {

	}

	@Override
	public void serialize(final SerializableDataOutputStream serializableDataOutputStream) throws IOException {

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
	public EntityNum getKey() {
		return null;
	}

	@Override
	public void setKey(final EntityNum entityNum) {

	}
}
