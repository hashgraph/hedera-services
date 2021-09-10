package com.hedera.services.usage.token.meta;

/*-
 * ‌
 * Hedera Services API Fees
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
import com.hederahashgraph.api.proto.java.SubType;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class TokenMintMeta {
	private final int bpt;
	private final long rbs;
	private final SubType subType;
	private final long transferRecordRb;

	public TokenMintMeta(final int bpt,
			final long rbs, final SubType subType,
			final long transferRecordRb) {
		this.bpt = bpt;
		this.rbs = rbs;
		this.subType = subType;
		this.transferRecordRb = transferRecordRb;
	}

	public long getRbs() { return rbs;}
	public SubType getSubType() { return subType;}
	public int getBpt() { return bpt;}
	public long getTransferRecordDb() { return transferRecordRb;}

	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("bpt", bpt)
				.add("transferRecordDb", transferRecordRb)
				.add("rbs", rbs)
				.add("subType", subType)
				.toString();
	}
}
