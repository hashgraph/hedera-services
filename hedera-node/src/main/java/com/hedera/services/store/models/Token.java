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
import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.utils.MiscUtils.describe;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;

/**
 * Encapsulates the state and operations of a Hedera token.
 *
 * Operations are validated, and throw a {@link com.hedera.services.exceptions.InvalidTransactionException}
 * with response code capturing the failure when one occurs.
 *
 * <b>NOTE:</b> Some operations will likely be moved to specializations
 * of this class as NFTs are fully supported. For example, a
 * {@link Token#mint(TokenRelationship, long)} signature only makes
 * sense for a token of type {@code FUNGIBLE_COMMON}; the signature for
 * a {@code NON_FUNGIBLE_UNIQUE} will likely be {@code mint(TokenRelationship, byte[])}.
 */
public class Token {
	private final Id id;

	private boolean supplyHasChanged;

	private long totalSupply;
	private JKey kycKey;
	private JKey freezeKey;
	private JKey supplyKey;
	private boolean frozenByDefault;
	private Account treasury;
	private Account autoRenewAccount;

	public Token(Id id) {
		this.id = id;
	}

	public void burn(TokenRelationship treasuryRel, long amount) {
		validateTrue(amount > 0, FAIL_INVALID, () ->
				"Cannot burn " + amount + " units of " + this + " from " + treasuryRel);
		changeSupply(treasuryRel, -amount, INVALID_TOKEN_BURN_AMOUNT);
	}

	public void mint(TokenRelationship treasuryRel, long amount) {
		validateTrue(amount > 0, FAIL_INVALID, () ->
				"Cannot mint " + amount + " units of " + this + " from " + treasuryRel);
		changeSupply(treasuryRel, +amount, INVALID_TOKEN_MINT_AMOUNT);
	}

	public TokenRelationship newRelationshipWith(Account account) {
		final var newRel = new TokenRelationship(this, account);
		if (hasFreezeKey() && frozenByDefault) {
			newRel.setFrozen(true);
		}
		newRel.setKycGranted(!hasKycKey());
		return newRel;
	}

	private void changeSupply(TokenRelationship treasuryRel, long amount, ResponseCodeEnum negSupplyCode) {
		validateTrue(treasuryRel != null, FAIL_INVALID, () ->
				"Cannot mint with a null treasuryRel");
		validateTrue(treasuryRel.hasInvolvedIds(id, treasury.getId()), FAIL_INVALID, () ->
				"Cannot change " + this + " supply (" + amount + ") with non-treasury rel " + treasuryRel);

		validateTrue(supplyKey != null, TOKEN_HAS_NO_SUPPLY_KEY);

		final long newTotalSupply = totalSupply + amount;
		validateTrue(newTotalSupply >= 0, negSupplyCode);

		final long newTreasuryBalance = treasuryRel.getBalance() + amount;
		validateTrue(newTreasuryBalance >= 0, INSUFFICIENT_TOKEN_BALANCE);

		setTotalSupply(newTotalSupply);
		treasuryRel.setBalance(newTreasuryBalance);
	}

	public Account getTreasury() {
		return treasury;
	}

	public void setTreasury(Account treasury) {
		this.treasury = treasury;
	}

	public Account getAutoRenewAccount() {
		return autoRenewAccount;
	}

	public void setAutoRenewAccount(Account autoRenewAccount) {
		this.autoRenewAccount = autoRenewAccount;
	}

	public long getTotalSupply() {
		return totalSupply;
	}

	public void initTotalSupply(long totalSupply) {
		this.totalSupply = totalSupply;
	}

	public void setTotalSupply(long totalSupply) {
		supplyHasChanged = true;
		this.totalSupply = totalSupply;
	}

	public void setSupplyKey(JKey supplyKey) {
		this.supplyKey = supplyKey;
	}

	public void setKycKey(JKey kycKey) {
		this.kycKey = kycKey;
	}

	public void setFreezeKey(JKey freezeKey) {
		this.freezeKey = freezeKey;
	}

	public boolean hasFreezeKey() {
		return freezeKey != null;
	}

	public boolean hasKycKey() {
		return kycKey != null;
	}

	public boolean hasChangedSupply() {
		return supplyHasChanged;
	}

	public boolean isFrozenByDefault() {
		return frozenByDefault;
	}

	public void setFrozenByDefault(boolean frozenByDefault) {
		this.frozenByDefault = frozenByDefault;
	}

	public Id getId() {
		return id;
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
		return MoreObjects.toStringHelper(Token.class)
				.add("id", id)
				.add("treasury", treasury)
				.add("autoRenewAccount", autoRenewAccount)
				.add("kycKey", describe(kycKey))
				.add("freezeKey", describe(freezeKey))
				.add("frozenByDefault", frozenByDefault)
				.add("supplyKey", describe(supplyKey))
				.toString();
	}

	public TokenID toGrpcId() {
		return TokenID.newBuilder()
				.setRealmNum(id.getRealm())
				.setShardNum(id.getShard())
				.setTokenNum(id.getNum())
				.build();
	}
}
