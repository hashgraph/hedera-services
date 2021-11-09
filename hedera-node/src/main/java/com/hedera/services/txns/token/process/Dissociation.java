package com.hedera.services.txns.token.process;

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
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.validation.OptionValidator;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;

public class Dissociation {
	private final TokenRelationship dissociatingAccountRel;
	private final TokenRelationship dissociatedTokenTreasuryRel;

	private boolean modelsAreUpdated = false;
	private boolean expiredTokenTreasuryReceivedBalance = false;

	public static Dissociation loadFrom(TypedTokenStore tokenStore, Account account, Id tokenId) {
		final var token = tokenStore.loadPossiblyDeletedOrAutoRemovedToken(tokenId);
		final var dissociatingAccountRel = tokenStore.loadTokenRelationship(token, account);
		if (token.isBelievedToHaveBeenAutoRemoved()) {
			return new Dissociation(dissociatingAccountRel, null);
		} else {
			final var treasury = token.getTreasury();
			final var dissociatedTokenTreasuryRel = tokenStore.loadPossiblyDeletedTokenRelationship(token, treasury);
			return new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);
		}
	}

	public Dissociation(
			TokenRelationship dissociatingAccountRel,
			@Nullable TokenRelationship dissociatedTokenTreasuryRel
	) {
		Objects.requireNonNull(dissociatingAccountRel);

		this.dissociatingAccountRel = dissociatingAccountRel;
		this.dissociatedTokenTreasuryRel = dissociatedTokenTreasuryRel;
	}

	public TokenRelationship dissociatingAccountRel() {
		return dissociatingAccountRel;
	}

	public Id dissociatingAccountId() {
		return dissociatingAccountRel.getAccount().getId();
	}

	public Id dissociatedTokenId() {
		return dissociatingAccountRel.getToken().getId();
	}

	public TokenRelationship dissociatedTokenTreasuryRel() {
		return dissociatedTokenTreasuryRel;
	}

	public void updateModelRelsSubjectTo(OptionValidator validator) {
		/* The token-treasury relationship is null either if (1) the token has been auto-removed; or
		(2) the token is deleted and its treasury has already been dissociated.
		In either case, there is nothing more to do or validate. */
		if (dissociatedTokenTreasuryRel != null) {
			/* Also nothing more to do for an association with a deleted token. */
			if (dissociatingAccountRel.getToken().isDeleted()) {
				updateModelsForDissociationFromDeletedToken();
			} else {
				updateModelsForDissociationFromActiveToken(validator);
			}
		}
		dissociatingAccountRel.markAsDestroyed();
		modelsAreUpdated = true;
	}

	private void updateModelsForDissociationFromDeletedToken() {
		Objects.requireNonNull(dissociatingAccountRel);

		final var disappearingUnits = dissociatingAccountRel.getBalance();
		dissociatingAccountRel.setBalance(0L);

		final var token = dissociatingAccountRel.getToken();
		if (token.getType() == NON_FUNGIBLE_UNIQUE) {
			final var account = dissociatingAccountRel.getAccount();
			final var curOwnedNfts = account.getOwnedNfts();
			account.setOwnedNfts(curOwnedNfts - disappearingUnits);
		}
	}

	private void updateModelsForDissociationFromActiveToken(OptionValidator validator) {
		Objects.requireNonNull(dissociatedTokenTreasuryRel);

		final var token = dissociatingAccountRel.getToken();
		final var isAccountTreasuryOfDissociatedToken =
				dissociatingAccountId().equals(token.getTreasury().getId());
		validateFalse(isAccountTreasuryOfDissociatedToken, ACCOUNT_IS_TREASURY);
		validateFalse(dissociatingAccountRel.isFrozen(), ACCOUNT_FROZEN_FOR_TOKEN);

		final var balance = dissociatingAccountRel.getBalance();
		if (balance > 0L) {
			validateFalse(token.getType() == NON_FUNGIBLE_UNIQUE, ACCOUNT_STILL_OWNS_NFTS);

			final var isTokenExpired = !validator.isAfterConsensusSecond(token.getExpiry());
			validateTrue(isTokenExpired, TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);

			/* If the fungible common token is expired, we automatically transfer the
			dissociating account's balance back to the treasury. */
			dissociatingAccountRel.setBalance(0L);
			final var newTreasuryBalance = dissociatedTokenTreasuryRel.getBalance() + balance;
			dissociatedTokenTreasuryRel.setBalance(newTreasuryBalance);
			expiredTokenTreasuryReceivedBalance = true;
		}
	}

	public void addUpdatedModelRelsTo(List<TokenRelationship> accumulator) {
		if (!modelsAreUpdated) {
			throw new IllegalStateException("Cannot reveal changed relationships before update");
		}
		if (expiredTokenTreasuryReceivedBalance) {
			final var treasuryId = dissociatedTokenTreasuryRel.getAccount().getId();
			if (Id.ID_COMPARATOR.compare(dissociatingAccountId(), treasuryId) < 0) {
				accumulator.add(dissociatingAccountRel);
				accumulator.add(dissociatedTokenTreasuryRel);
			} else {
				accumulator.add(dissociatedTokenTreasuryRel);
				accumulator.add(dissociatingAccountRel);
			}
		} else {
			accumulator.add(dissociatingAccountRel);
		}
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(Dissociation.class)
				.add("dissociatingAccountId", dissociatingAccountRel.getAccount().getId())
				.add("dissociatedTokenId", dissociatingAccountRel.getToken().getId())
				.add("dissociatedTokenTreasuryId", dissociatedTokenTreasuryRel.getAccount().getId())
				.add("expiredTokenTreasuryReceivedBalance", expiredTokenTreasuryReceivedBalance)
				.toString();
	}
}
