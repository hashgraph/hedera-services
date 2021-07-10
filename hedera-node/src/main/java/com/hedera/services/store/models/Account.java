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
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.internals.CopyOnWriteIds;
import com.hedera.services.txns.token.process.DissociationRels;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;

/**
 * Encapsulates the state and operations of a Hedera account.
 * <p>
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
	private long ownedNfts;

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

	public long getOwnedNfts() {
		return ownedNfts;
	}

	public void setOwnedNfts(long ownedNfts) {
		this.ownedNfts = ownedNfts;
	}

	public void incrementOwnedNfts() {
		this.ownedNfts++;
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

	public void dissociateUsing(List<DissociationRels> dissociations, OptionValidator validator) {
		final Set<Id> dissociatedTokenIds = new HashSet<>();
		for (var dissociation : dissociations) {
			validateTrue(id.equals(dissociation.dissociatingAccountId()), FAIL_INVALID);
			dissociation.updateModelRelsSubjectTo(validator);
			dissociatedTokenIds.add(dissociation.dissociatedTokenId());
		}
		associatedTokens.removeAllIds(dissociatedTokenIds);
	}

	/**
	 * Performs validation and dissociation - removing relationships between tokens and accounts.
	 * Expired tokens of type {@link TokenType#FUNGIBLE_COMMON} are transferred back to the treasury
	 *
	 * @param relations a list of {@link TokenRelationship}, {@link TokenRelationship} pairs
	 * @param validator - injected {@link OptionValidator} used to verify whether the token is expired
	 */
	public void dissociateWith(List<Pair<TokenRelationship, TokenRelationship>> relations, final OptionValidator validator) {
		final Set<Id> uniqueIds = new HashSet<>();
		for (var rel : relations) {
			/*	Extraction	 */
			final var accountRel = rel.getKey();
			final var treasuryRel = rel.getValue();
			final var account = accountRel.getAccount();
			final var treasury = treasuryRel.getAccount();
			final var token = accountRel.getToken();

			/*	Validation	 */
			validateTrue(associatedTokens.contains(token.getId()), TOKEN_NOT_ASSOCIATED_TO_ACCOUNT,
					"Given account is not associated to the given token");
			if (!token.isDeleted()) {
				validateFalse(treasury.getId().equals(account.getId()), ACCOUNT_IS_TREASURY,
						"Given account is treasury");
				validateFalse(accountRel.isFrozen(), ACCOUNT_FROZEN_FOR_TOKEN,
						"Account is frozen for given token");
			}

			var tokenBalance = accountRel.getBalance();
			if (tokenBalance > 0) {
				validateTrue(token.getType().equals(TokenType.FUNGIBLE_COMMON), TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES,
						"Cannot dissociate given account while it has " + accountRel.getBalance() + " instances of unique tokens");

				final var expiry = Timestamp.newBuilder().setSeconds(token.getExpiry()).build();
				final var isTokenExpired = !validator.isValidExpiry(expiry);
				validateFalse(!isTokenExpired && !token.isDeleted(), TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);

				if (!token.isDeleted()) {
					/* Must be expired; return tokenBalance to treasury account. */
					treasuryRel.adjustBalance(tokenBalance);
					accountRel.adjustBalance(-tokenBalance);
				}
			}
			uniqueIds.add(token.getId());
		}
		/* Commit	 */
		associatedTokens.removeAllIds(uniqueIds);
	}

	public Id getId() {
		return id;
	}

	public CopyOnWriteIds getAssociatedTokens() {
		return associatedTokens;
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
