package com.hedera.services.state.merkle;

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

import com.google.common.base.MoreObjects;
import com.swirlds.common.FCMValue;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.io.SerializedObjectProvider;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleNode;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.DataInputStream;
import java.io.IOException;

public class MerkleTokenRelStatus extends AbstractMerkleNode implements FCMValue, MerkleLeaf {
	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xe487c7b8b4e7233fL;

	private long balance;
	private boolean frozen;
	private boolean kycGranted;

	@Deprecated
	public static final MerkleTokenRelStatus.Provider LEGACY_PROVIDER = new MerkleTokenRelStatus.Provider();

	public MerkleTokenRelStatus() { }

	public MerkleTokenRelStatus(long balance, boolean frozen, boolean kycGranted) {
		this.balance = balance;
		this.frozen = frozen;
		this.kycGranted = kycGranted;
	}

	@Deprecated
	public static class Provider implements SerializedObjectProvider {
		@Override
		public FastCopyable deserialize(DataInputStream in) throws IOException {
			throw new UnsupportedOperationException();
		}
	}

	/* --- MerkleLeaf --- */
	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		balance = in.readLong();
		frozen = in.readBoolean();
		kycGranted = in.readBoolean();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(balance);
		out.writeBoolean(frozen);
		out.writeBoolean(kycGranted);
	}

	/* --- Object --- */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleTokenRelStatus.class != o.getClass()) {
			return false;
		}

		var that = (MerkleTokenRelStatus)o;
		return new EqualsBuilder()
				.append(balance, that.balance)
				.append(frozen, that.frozen)
				.append(kycGranted, that.kycGranted)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(balance)
				.append(frozen)
				.append(kycGranted)
				.toHashCode();
	}

	/* --- Bean --- */

	public long getBalance() {
		return balance;
	}

	public void setBalance(long balance) {
		this.balance = balance;
	}

	public boolean isFrozen() {
		return frozen;
	}

	public void setFrozen(boolean frozen) {
		this.frozen = frozen;
	}

	public boolean isKycGranted() {
		return kycGranted;
	}

	public void setKycGranted(boolean kycGranted) {
		this.kycGranted = kycGranted;
	}

	/* --- FastCopyable --- */
	@Override
	public MerkleTokenRelStatus copy() {
		return new MerkleTokenRelStatus(balance, frozen, kycGranted);
	}

	@Override
	public void delete() { }

	@Override
	@Deprecated
	public void copyFrom(SerializableDataInputStream in) {
		throw new UnsupportedOperationException();
	}

	@Override
	@Deprecated
	public void copyFromExtra(SerializableDataInputStream in) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("balance", balance)
				.add("isFrozen", frozen)
				.add("hasKycGranted", kycGranted)
				.toString();
	}
}
