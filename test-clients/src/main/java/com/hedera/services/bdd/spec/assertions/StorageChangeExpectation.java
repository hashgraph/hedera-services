package com.hedera.services.bdd.spec.assertions;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;

public class StorageChangeExpectation {
	private ByteString slot;
	private ByteString valueRead;
	private BytesValue valueWritten;

	private StorageChangeExpectation(ByteString slot, ByteString value) {
		this.slot = slot;
		this.valueRead = value;
	}

	private StorageChangeExpectation(ByteString slot, ByteString prevValue, BytesValue value) {
		this.slot = slot;
		this.valueRead = prevValue;
		this.valueWritten = value;
	}

	public static StorageChangeExpectation onlyRead(ByteString slot, ByteString value) {
		return new StorageChangeExpectation(slot, value);
	}

	public static StorageChangeExpectation readAndWritten(ByteString slot, ByteString prevValue, ByteString value) {
		return new StorageChangeExpectation(slot, prevValue, BytesValue.of(value));
	}

	public ByteString getSlot() {
		return this.slot;
	}

	public ByteString getValueRead() {
		return this.valueRead;
	}

	public BytesValue getValueWritten() {
		return this.valueWritten;
	}
}
