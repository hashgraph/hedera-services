package com.hedera.services.state.submerkle;

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
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;

/**
 * Process self serializable object that represents a
 * This is useful for setting new token associations created by the active transaction {@link ExpirableTxnRecord}.
 */
public class FcTokenAssociation implements SelfSerializable {

	static final int RELEASE_0180_VERSION = 1;
	static final int CURRENT_VERSION = RELEASE_0180_VERSION;

	static final long RUNTIME_CONSTRUCTABLE_ID = 0x41a2569130b01d2fL;

	private EntityId tokenId;
	private EntityId accountId;

	public FcTokenAssociation() {
		/* For RuntimeConstructable */
	}

	public FcTokenAssociation(
			final EntityId tokenId,
			final EntityId accountId
	) {
		this.tokenId = tokenId;
		this.accountId = accountId;
	}
	public EntityId token() {
		return tokenId;
	}

	public EntityId account() {
		return accountId;
	}

	@Override
	public boolean equals(final Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(FcTokenAssociation.class)
				.add("token", tokenId)
				.add("account", accountId)
				.toString();
	}

	public TokenAssociation toGrpc() {
		return TokenAssociation.newBuilder()
							.setAccountId(accountId.toGrpcAccountId())
							.setTokenId(tokenId.toGrpcTokenId())
							.build();
	}

	public static FcTokenAssociation fromGrpc(TokenAssociation tokenAssociation) {
		return new FcTokenAssociation(
				EntityId.fromGrpcTokenId(tokenAssociation.getTokenId()),
				EntityId.fromGrpcAccountId(tokenAssociation.getAccountId()));
	}

	@Override
	public void deserialize(final SerializableDataInputStream in,
			final int version) throws IOException {
		tokenId = in.readSerializable();
		accountId = in.readSerializable();
	}

	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeSerializable(tokenId, true);
		out.writeSerializable(accountId, true);
	}

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return CURRENT_VERSION;
	}
}
