package com.hedera.services.state.merkle.internals;

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

import java.util.Objects;

import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.CONTRACT_BYTECODE;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.CONTRACT_STORAGE;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.FILE_DATA;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.FILE_METADATA;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.SYSTEM_DELETED_ENTITY_EXPIRY;

public class BlobKey {
	public enum BlobType {
		FILE_DATA, FILE_METADATA, CONTRACT_STORAGE, CONTRACT_BYTECODE, SYSTEM_DELETED_ENTITY_EXPIRY
	}

	private final BlobType type;
	private final long entityNum;

	public BlobKey(BlobType type, long entityNum) {
		this.type = type;
		this.entityNum = entityNum;
	}

	public BlobType getType() {
		return type;
	}

	public long getEntityNum() {
		return entityNum;
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this) {
			return true;
		}
		if (o == null || BlobKey.class != o.getClass()) {
			return false;
		}
		final var that = (BlobKey) o;
		return this.type == that.type && this.entityNum == that.entityNum;
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, entityNum);
	}

	@Override
	public String toString() {
		return "BlobKey{" +
				"type=" + type +
				", entityNum=" + entityNum +
				'}';
	}

	/**
	 * Returns the type corresponding to a legacy character code.
	 *
	 * @param code the legacy blob code
	 * @return the blob type
	 */
	public static BlobType typeFromCharCode(final char code) {
		switch (code) {
			case 'f':
				return FILE_DATA;
			case 'k':
				return FILE_METADATA;
			case 's':
				return CONTRACT_BYTECODE;
			case 'd':
				return CONTRACT_STORAGE;
			case 'e':
				return SYSTEM_DELETED_ENTITY_EXPIRY;
			default:
				throw new IllegalArgumentException("Invalid legacy code '" + code + "'");
		}
	}
}
