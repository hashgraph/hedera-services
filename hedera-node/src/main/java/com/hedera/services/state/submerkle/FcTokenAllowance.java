/*
 * -
 *  * ‌
 *  * Hedera Services Node
 *  * ​
 *  * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 *  * ​
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  * ‍
 *
 */

package com.hedera.services.state.submerkle;

import com.google.common.base.MoreObjects;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;
import java.util.List;

public class FcTokenAllowance implements SelfSerializable {
	static final int RELEASE_023X_VERSION = 1;
	static final int CURRENT_VERSION = RELEASE_023X_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xf65baa533950f139L;

	private long allowance;
	private boolean approvedForAll;
	private List<Long> serialNumbers;

	public FcTokenAllowance() {
		/* RuntimeConstructable */
	}

	FcTokenAllowance(final long allowance, final boolean approvedForAll, final List<Long> serialNumbers) {
		this.allowance = allowance;
		this.approvedForAll = approvedForAll;
		this.serialNumbers = serialNumbers;
	}

	FcTokenAllowance(final long allowance) {
		this.allowance = allowance;
		/* approvedForAll will be false, serialNums is null */
	}

	FcTokenAllowance(final boolean approvedForAll) {
		this.approvedForAll = approvedForAll;
		/* allowance will be null, serialNums is null*/
	}

	FcTokenAllowance(final List<Long> serialNumbers) {
		this.serialNumbers = serialNumbers;
		/* allowance will be null, approvedForAll is false */
	}

	@Override
	public void deserialize(final SerializableDataInputStream din, final int i) throws IOException {
		approvedForAll = din.readBoolean();
		allowance = din.readLong();
		final var isForNfts = din.readBoolean();
		if (isForNfts) {
			serialNumbers = din.readLongList(Integer.MAX_VALUE);
		}
	}

	@Override
	public void serialize(final SerializableDataOutputStream dos) throws IOException {
		dos.writeBoolean(approvedForAll);
		dos.writeLong(allowance);
		dos.writeBoolean(serialNumbers != null);
		if (serialNumbers != null) {
			dos.writeLongList(serialNumbers);
		}
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
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || !obj.getClass().equals(FcTokenAllowance.class)) {
			return false;
		}

		final var that = (FcTokenAllowance) obj;
		return new EqualsBuilder()
				.append(allowance, that.allowance)
				.append(approvedForAll, that.approvedForAll)
				.append(serialNumbers, that.serialNumbers)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder()
				.append(allowance)
				.append(approvedForAll)
				.append(serialNumbers)
				.toHashCode();
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.omitNullValues()
				.add("allowance", allowance)
				.add("approvedForAll", approvedForAll)
				.add("serialNumbers", serialNumbers)
				.toString();
	}

	public long getAllowance() {
		return allowance;
	}

	public boolean isApprovedForAll() {
		return approvedForAll;
	}

	public List<Long> getSerialNumbers() {
		return serialNumbers;
	}

	public static FcTokenAllowance from(
			final long allowance,
			final boolean approvedForAll,
			final List<Long> serialNumbers) {
		return new FcTokenAllowance(allowance, approvedForAll, serialNumbers);
	}

	public static FcTokenAllowance from(final long allowance) {
		return new FcTokenAllowance(allowance);
	}

	public static FcTokenAllowance from(final boolean approvedForAll) {
		return new FcTokenAllowance(approvedForAll);
	}

	public static FcTokenAllowance from(final List<Long> serialNumbers) {
		return new FcTokenAllowance(serialNumbers);
	}
}
