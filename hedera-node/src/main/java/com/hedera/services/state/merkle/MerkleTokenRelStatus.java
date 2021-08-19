package com.hedera.services.state.merkle;

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

import com.google.common.base.MoreObjects;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;

public class MerkleTokenRelStatus extends AbstractMerkleLeaf {
	static final int RELEASE_090_VERSION = 1;
	static final int RELEASE_0180_VERSION = 2;
	static final int MERKLE_VERSION = RELEASE_0180_VERSION;

	static final long RUNTIME_CONSTRUCTABLE_ID = 0xe487c7b8b4e7233fL;

	private long balance;
	private boolean frozen;
	private boolean kycGranted;
	private boolean automaticAssociation;

	public MerkleTokenRelStatus() {
	}

	public MerkleTokenRelStatus(long balance, boolean frozen, boolean kycGranted, boolean automaticAssociation) {
		this.balance = balance;
		this.frozen = frozen;
		this.kycGranted = kycGranted;
		this.automaticAssociation = automaticAssociation;
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
		if (version >= RELEASE_0180_VERSION) {
			automaticAssociation = in.readBoolean();
		}
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(balance);
		out.writeBoolean(frozen);
		out.writeBoolean(kycGranted);
		out.writeBoolean(automaticAssociation);
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

		var that = (MerkleTokenRelStatus) o;
		return new EqualsBuilder()
				.append(balance, that.balance)
				.append(frozen, that.frozen)
				.append(kycGranted, that.kycGranted)
				.append(automaticAssociation, that.automaticAssociation)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(balance)
				.append(frozen)
				.append(kycGranted)
				.append(automaticAssociation)
				.toHashCode();
	}

	/* --- Bean --- */
	public long getBalance() {
		return balance;
	}

	public void setBalance(long balance) {
		throwIfImmutable("Cannot change this token relation's balance if it's immutable.");
		if (balance < 0) {
			throw new IllegalArgumentException(String.format("Argument 'balance=%d' would negate %s!", balance, this));
		}
		this.balance = balance;
	}

	public boolean isFrozen() {
		return frozen;
	}

	public void setFrozen(boolean frozen) {
		throwIfImmutable("Cannot change this token relation's frozen status if it's immutable.");
		this.frozen = frozen;
	}

	public boolean isKycGranted() {
		return kycGranted;
	}

	public void setKycGranted(boolean kycGranted) {
		throwIfImmutable("Cannot change this token relation's grant kyc if it's immutable.");
		this.kycGranted = kycGranted;
	}

	public boolean isAutomaticAssociation() {
		return automaticAssociation;
	}

	public void setAutomaticAssociation(boolean automaticAssociation) {
		throwIfImmutable("Cannot change this token relation's automaticAssociation if it's immutable.");
		this.automaticAssociation = automaticAssociation;
	}

	/* --- FastCopyable --- */
	@Override
	public MerkleTokenRelStatus copy() {
		setImmutable(true);
		return new MerkleTokenRelStatus(balance, frozen, kycGranted, automaticAssociation);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("balance", balance)
				.add("isFrozen", frozen)
				.add("hasKycGranted", kycGranted)
				.add("isAutomaticAssociation", automaticAssociation)
				.toString();
	}
}
