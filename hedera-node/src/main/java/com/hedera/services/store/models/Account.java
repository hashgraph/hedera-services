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
import com.hedera.services.state.merkle.internals.CopyOnWriteIds;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;

/**
 * Encapsulates the state and operations of a Hedera account.
 *
 * Operations are validated, and throw a {@link com.hedera.services.exceptions.InvalidTransactionException}
 * with response code capturing the failure when one occurs.
 *
 * <b>NOTE:</b> This implementation is incomplete, and includes
 * only the API needed to support the Hedera Token Service. The
 * memo field, for example, is not yet present.
 */
public class Account {
	private final Id id;

	private long expiry;
	private long balance;
	private boolean deleted = false;
	private CopyOnWriteIds associatedTokens;

	public Account(Id id) {
		this.id = id;
	}

	public void setAssociatedTokens(CopyOnWriteIds associatedTokens) {
		this.associatedTokens = associatedTokens;
	}

	public void setExpiry(long expiry) {
		this.expiry = expiry;
	}

	public void initBalance(long balance) {
		this.balance = balance;
	}

	public void associateWith(List<Token> tokens, int maxAllowed) {
		final var alreadyAssociated = associatedTokens.size();
		final var proposedNewAssociations = tokens.size() + alreadyAssociated;
		validateTrue(proposedNewAssociations <= maxAllowed, TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

		final Set<Id> uniqueIds = new HashSet<>();
		for (var token : tokens) {
			final var id = token.getId();
			validateFalse(associatedTokens.contains(id), TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT);
			uniqueIds.add(id);
		}

		associatedTokens.addAllIds(uniqueIds);
	}

	public Id getId() {
		return id;
	}

	public CopyOnWriteIds getAssociatedTokens() {
		return associatedTokens;
	}

	public AccountID toGrpcId() {
		return AccountID.newBuilder()
				.setRealmNum(id.getRealm())
				.setShardNum(id.getShard())
				.setAccountNum(id.getNum())
				.build();
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
		final var assocTokenRepr = Optional.ofNullable(associatedTokens)
				.map(CopyOnWriteIds::toReadableIdList)
				.orElse("<N/A>");
		return MoreObjects.toStringHelper(Account.class)
				.add("id", id)
				.add("expiry", expiry)
				.add("balance", balance)
				.add("deleted", deleted)
				.add("tokens", assocTokenRepr)
				.toString();
	}
}
