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

public class BlobKey {
	public enum BlobType {
		FILE_DATA, FILE_METADATA, BYTECODE, SYSTEM_DELETION_TIME
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
}
