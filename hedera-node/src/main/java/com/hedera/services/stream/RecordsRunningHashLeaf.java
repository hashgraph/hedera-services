package com.hedera.services.stream;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Objects;

/**
 * Contains current {@code com.swirlds.common.crypto.RunningHash) which contains a Hash which is a running
 * Hash calculated from all {@link RecordStreamObject} in history
 */
public class RecordsRunningHashLeaf extends AbstractMerkleLeaf {
	private static final Logger log = LogManager.getLogger(RecordsRunningHashLeaf.class);

	public static final long CLASS_ID = 0xe370929ba5429d9bL;
	public static final int CLASS_VERSION = 1;
	/**
	 * a runningHash of all RecordStreamObject
	 */
	private RunningHash runningHash;

	/**
	 * no-args constructor required by ConstructableRegistry
	 */
	public RecordsRunningHashLeaf() {
	}

	public RecordsRunningHashLeaf(final RunningHash runningHash) {
		this.runningHash = runningHash;
	}

	private RecordsRunningHashLeaf(final RecordsRunningHashLeaf runningHashLeaf) {
		this.runningHash = runningHashLeaf.runningHash;
		setImmutable(false);
		runningHashLeaf.setImmutable(true);
		setHash(runningHashLeaf.getHash());
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		try {
			// should wait until runningHash has been calculated and set
			out.writeSerializable(runningHash.getFutureHash().get(), true);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IOException("Got interrupted when getting runningHash when serializing RunningHashLeaf",
					ex);
		}
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		runningHash = new RunningHash();
		runningHash.setHash(in.readSerializable());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isDataExternal() {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		RecordsRunningHashLeaf that = (RecordsRunningHashLeaf) o;
		if (this.runningHash.getHash() != null && that.runningHash.getHash() != null) {
			return this.runningHash.getHash().equals(that.runningHash.getHash());
		}
		return new EqualsBuilder().append(this.runningHash, that.runningHash).isEquals();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return Objects.hash(runningHash);
	}

	public RecordsRunningHashLeaf copy() {
		// throwIfImmutable();
		return new RecordsRunningHashLeaf(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return CLASS_VERSION;
	}

	@Override
	public String toString() {
		return String.format("RecordsRunningHashLeaf's Hash: %s, Hash contained in the leaf: %s",
				getHash(),
				runningHash.getHash());
	}

	public RunningHash getRunningHash() {
		return runningHash;
	}

	public void setRunningHash(final RunningHash runningHash) {
		this.runningHash = runningHash;
		// should invalidate current Hash when updating the runningHash object
		// because its Hash should be calculated based on the runningHash object
		this.invalidateHash();
	}
}
