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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;

public class DissociationRels {
	private final TokenRelationship dissociatingAccountRel;
	private final TokenRelationship dissociatedTokenTreasuryRel;

	private boolean modelsAreUpdated = false;
	private boolean expiredTokenTreasuryReceivedBalance = false;

	public static DissociationRels loadFrom(TypedTokenStore tokenStore, Account account, Id tokenId) {
		final var token = tokenStore.loadPossiblyDeletedOrAutoRemovedToken(tokenId);
		final var dissociatingAccountRel = tokenStore.loadTokenRelationship(token, account);
		if (token.isBelievedToHaveBeenAutoRemoved()) {
			return new DissociationRels(dissociatingAccountRel, null);
		} else {
			final var treasury = token.getTreasury();
			final var dissociatedTokenTreasuryRel = tokenStore.loadTokenRelationship(token, treasury);
			return new DissociationRels(dissociatingAccountRel, dissociatedTokenTreasuryRel);
		}
	}

	public DissociationRels(
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

	public boolean didExpiredTokenTreasuryReceiveBalance() {
		return expiredTokenTreasuryReceivedBalance;
	}

	public void updateModelRelsSubjectTo(OptionValidator validator) {
		if (dissociatedTokenTreasuryRel == null) {
			dissociatingAccountRel.markAsDestroyed();
			return;
		}

		final var isAccountTreasuryOfDissociatedToken =
				dissociatingAccountId().equals(dissociatedTokenTreasuryRel.getToken().getTreasury().getId());
		validateFalse(isAccountTreasuryOfDissociatedToken, ACCOUNT_IS_TREASURY);

		validateFalse(dissociatingAccountRel.isFrozen(), ACCOUNT_FROZEN_FOR_TOKEN);

		throw new AssertionError("Not implemented!");
	}

	public void addUpdatedModelRelsTo(List<TokenRelationship> accumulator) {
		accumulator.add(dissociatingAccountRel);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(DissociationRels.class)
				.add("dissociatingAccountId", dissociatingAccountRel.getAccount().getId())
				.add("dissociatedTokenId", dissociatingAccountRel.getToken().getId())
				.add("dissociatedTokenTreasuryId", dissociatedTokenTreasuryRel.getAccount().getId())
				.add("expiredTokenTreasuryReceivedBalance", expiredTokenTreasuryReceivedBalance)
				.toString();
	}

	void expiredTokenTreasuryReceivedBalance() {
		this.expiredTokenTreasuryReceivedBalance = true;
	}
}
