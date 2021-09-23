package com.hedera.services.store.models;

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
import com.hedera.services.state.submerkle.RichInstant;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Encapsulates the state and operations of a Hedera Unique token.
 *
 * Operations are validated, and throw a {@link com.hedera.services.exceptions.InvalidTransactionException}
 * with response code capturing the failure when one occurs.
 */
public class UniqueToken {
	private Id tokenId;
	private long serialNumber;
	private RichInstant creationTime;
	private Id owner;
	private byte[] metadata;

	public UniqueToken(Id tokenId, long serialNumber) {
		this.tokenId = tokenId;
		this.serialNumber = serialNumber;
	}

	public UniqueToken(Id tokenId, long serialNumber, Id owner) {
		this.tokenId = tokenId;
		this.serialNumber = serialNumber;
		this.owner = owner;
	}

	public UniqueToken(Id tokenId, long serialNumber, RichInstant creationTime, Id owner, byte[] metadata) {
		this.tokenId = tokenId;
		this.serialNumber = serialNumber;
		this.creationTime = creationTime;
		this.owner = owner;
		this.metadata = metadata;
	}

	public Id getTokenId() {
		return tokenId;
	}

	public void setTokenId(Id tokenId) {
		this.tokenId = tokenId;
	}

	public long getSerialNumber() {
		return serialNumber;
	}

	public void setSerialNumber(long serialNumber) {
		this.serialNumber = serialNumber;
	}

	public RichInstant getCreationTime() {
		return creationTime;
	}

	public void setCreationTime(RichInstant creationTime) {
		this.creationTime = creationTime;
	}

	public Id getOwner() {
		return owner;
	}

	public void setOwner(Id owner) {
		this.owner = owner;
	}

	public byte[] getMetadata() {
		return metadata;
	}

	public void setMetadata(byte[] metadata) {
		this.metadata = metadata;
	}

	/* NOTE: The object methods below are only overridden to improve
		readability of unit tests; this model object is not used in hash-based
		collections, so the performance of these methods doesn't matter. */
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
		return MoreObjects.toStringHelper(UniqueToken.class)
				.add("owner", owner)
				.add("tokenId", tokenId)
				.add("serialNum", serialNumber)
				.add("creationTime", creationTime)
				.add("metadata", metadata)
				.toString();
	}
}
