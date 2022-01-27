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

public class FcAllowance implements SelfSerializable {
	static final int RELEASE_023X_VERSION = 1;
	static final int CURRENT_VERSION = RELEASE_023X_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xf65baa533950f139L;

	private Long allowance;
	private boolean approvedForAll;

	public FcAllowance() {
		/* RuntimeConstructable */
	}

	FcAllowance(final Long allowance, final boolean approvedForAll) {
		this.allowance = allowance;
		this.approvedForAll = approvedForAll;
	}

	FcAllowance(final Long allowance) {
		this.allowance = allowance;
		/* approvedForAll will be false */
	}

	FcAllowance(final boolean approvedForAll) {
		this.approvedForAll = approvedForAll;
		/* allowance will be null */
	}

	@Override
	public void deserialize(final SerializableDataInputStream din, final int i) throws IOException {
		final var isAllowance = din.readBoolean();
		if (isAllowance) {
			allowance = din.readLong();
		}
		approvedForAll = din.readBoolean();
	}

	@Override
	public void serialize(final SerializableDataOutputStream dos) throws IOException {
		dos.writeBoolean(allowance != null);
		if (allowance != null) {
			dos.writeLong(allowance);
		}
		dos.writeBoolean(approvedForAll);
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
		if (obj == null || !obj.getClass().equals(FcAllowance.class)) {
			return false;
		}

		final var that = (FcAllowance) obj;
		return new EqualsBuilder()
				.append(allowance, that.allowance)
				.append(approvedForAll, that.approvedForAll)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder()
				.append(allowance)
				.append(approvedForAll)
				.toHashCode();
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.omitNullValues()
				.add("allowance", allowance)
				.add("approvedForAll", approvedForAll)
				.toString();
	}

	public Long getAllowance() {
		return allowance;
	}

	public boolean isApprovedForAll() {
		return approvedForAll;
	}

	public static FcAllowance from(final Long allowance, final boolean approvedForAll) {
		return new FcAllowance(allowance, approvedForAll);
	}

	public static FcAllowance from(final Long allowance) {
		return new FcAllowance(allowance);
	}

	public static FcAllowance from(final boolean approvedForAll) {
		return new FcAllowance(approvedForAll);
	}
}
