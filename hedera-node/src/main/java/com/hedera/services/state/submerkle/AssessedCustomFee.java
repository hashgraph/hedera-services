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
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import proto.CustomFeesOuterClass;

import java.io.IOException;

/**
 * Process self serializable object that represents an assessed custom fee, which may or may not result in a balance
 * change, depending on if the payer can afford it.
 * This is useful for setting custom fees balance changes in {@link ExpirableTxnRecord}.
 */
public class AssessedCustomFee implements SelfSerializable {
	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xd8b56ce46e56a466L;

	private EntityId token;
	private EntityId account;
	private long units;

	public AssessedCustomFee() {
		/* For RuntimeConstructable */
	}

	private AssessedCustomFee(final EntityId token, final AccountAmount aa) {
		this.token = token;
		this.account = EntityId.fromGrpcAccountId(aa.getAccountID());
		this.units = aa.getAmount();
	}

	public AssessedCustomFee(final EntityId account, final long amount) {
		this.token = null;
		this.account = account;
		this.units = amount;
	}

	public AssessedCustomFee(final EntityId account, final EntityId token, final long amount) {
		this.token = token;
		this.account = account;
		this.units = amount;
	}

	public boolean isForHbar() {
		return token == null;
	}

	public long units() {
		return units;
	}

	public EntityId token() {
		return token;
	}

	public EntityId account() {
		return account;
	}

	public static AssessedCustomFee assessedHbarFeeFrom(final AccountAmount aa) {
		return new AssessedCustomFee(null, aa);
	}

	public static AssessedCustomFee assessedHtsFeeFrom(final EntityId token, final AccountAmount aa) {
		return new AssessedCustomFee(token, aa);
	}

	/* NOTE: The object methods below are only overridden to improve readability of unit tests;
	this model object is not used in hash-based collections, so the performance of these
	methods doesn't matter. */

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
		return MoreObjects.toStringHelper(AssessedCustomFee.class)
				.add("token", token == null ? "ℏ" : token)
				.add("account", account)
				.add("units", units)
				.toString();
	}

	/* --- Helpers --- */
	public CustomFeesOuterClass.AssessedCustomFee toGrpc() {
		var grpc = CustomFeesOuterClass.AssessedCustomFee.newBuilder()
				.setFeeCollectorAccountId(account.toGrpcAccountId())
				.setAmount(units);
		if (isForHbar()) {
			return grpc.build();
		}
		return grpc.setTokenId(token.toGrpcTokenId()).build();
	}

	public static AssessedCustomFee fromGrpc(CustomFeesOuterClass.AssessedCustomFee assessedFee) {
		final var aa = AccountAmount.newBuilder()
				.setAccountID(assessedFee.getFeeCollectorAccountId())
				.setAmount(assessedFee.getAmount())
				.build();
		if (assessedFee.hasTokenId()) {
			return assessedHtsFeeFrom(EntityId.fromGrpcTokenId(assessedFee.getTokenId()), aa);
		}
		return assessedHbarFeeFrom(aa);
	}

	/* ----- SelfSerializable methods ------ */
	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		account = in.readSerializable();
		token = in.readSerializable();
		units = in.readLong();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeSerializable(account, true);
		out.writeSerializable(token, true);
		out.writeLong(units);
	}

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}
}
