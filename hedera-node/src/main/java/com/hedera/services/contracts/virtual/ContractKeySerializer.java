package com.hedera.services.contracts.virtual;

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

import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ContractKeySerializer implements KeySerializer<ContractKey> {
	@Override
	public int deserializeKeySize(ByteBuffer byteBuffer) {
		return 52; // TODO:
	}

	@Override
	public int getSerializedSize() {
		return 52; // TODO:
	}

	@Override
	public long getCurrentDataVersion() {
		return 1;
	}

	@Override
	public ContractKey deserialize(ByteBuffer byteBuffer, long dataVersion) throws IOException {
		final ContractKey key = new ContractKey();
		key.deserialize(byteBuffer, (int) dataVersion);
		return key;
	}

	@Override
	public int serialize(ContractKey contractKey, SerializableDataOutputStream serializableDataOutputStream) throws IOException {
		contractKey.serialize(serializableDataOutputStream);
		return 52; // TODO:
	}
}
