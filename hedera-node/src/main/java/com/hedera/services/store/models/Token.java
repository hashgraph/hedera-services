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
import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.state.submerkle.RichInstant;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.utils.MiscUtils.describe;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_MAX_SUPPLY_REACHED;

/**
 * Encapsulates the state and operations of a Hedera token.
 * <p>
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
	private final List<UniqueToken> mintedUniqueTokens = new ArrayList<>();
	private final List<UniqueToken> burnedUniqueTokens = new ArrayList<>();

	private TokenType type;
	private TokenSupplyType supplyType;
	private long totalSupply;
	private long maxSupply;
	private JKey kycKey;
	private JKey freezeKey;
	private JKey supplyKey;
	private JKey feeScheduleKey;
	private boolean frozenByDefault;
	private Account treasury;
	private Account autoRenewAccount;
	List<FcCustomFee> feeSchedule;

	private long lastUsedSerialNumber;

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
		validateTrue(type == TokenType.FUNGIBLE_COMMON, FAIL_INVALID, () ->
				"Fungible mint can be invoked only on Fungible token type");

		changeSupply(treasuryRel, +amount, INVALID_TOKEN_MINT_AMOUNT);
	}

	public void burn(
			final OwnershipTracker ownershipTracker,
			final TokenRelationship treasuryRelationship,
			final List<Long> serialNumbers
	){
		validateTrue( type == TokenType.NON_FUNGIBLE_UNIQUE, FAIL_INVALID, () ->
				"Non fungible burn can be invoked only on Non fungible tokens!");
		validateTrue( serialNumbers.size() > 0 , FAIL_INVALID, ()->
				"Non fungible burn cannot be invoked with no serial numbers");
		for (final long serialNum : serialNumbers) {
			ownershipTracker.add(id, OwnershipTracker.fromBurning(id, serialNum));
			burnedUniqueTokens.add(new UniqueToken(id, serialNum));
		}
		treasury.setOwnedNfts(treasury.getOwnedNfts() - serialNumbers.size());
		changeSupply(treasuryRelationship, -serialNumbers.size(), FAIL_INVALID);
	}

	public void mint(
			final OwnershipTracker ownershipTracker,
			final TokenRelationship treasuryRel,
			final List<ByteString> metadata,
			final RichInstant creationTime
	) {
		validateTrue(metadata.size() > 0, FAIL_INVALID, () ->
				"Cannot mint " + metadata.size() + " numbers of Unique Tokens");
		validateTrue(type == TokenType.NON_FUNGIBLE_UNIQUE, FAIL_INVALID, () ->
				"Non fungible mint can be invoked only on Non fungible token type");

		changeSupply(treasuryRel, metadata.size(), FAIL_INVALID);

		for (ByteString m : metadata) {
			lastUsedSerialNumber++;
			var uniqueToken = new UniqueToken(id, lastUsedSerialNumber, creationTime, treasury.getId(), m.toByteArray());
			mintedUniqueTokens.add(uniqueToken);
			ownershipTracker.add(id, OwnershipTracker.fromMinting(treasury.getId(), lastUsedSerialNumber));
		}
		treasury.setOwnedNfts(treasury.getOwnedNfts() + metadata.size());
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

		if (supplyType == TokenSupplyType.FINITE) {
			validateTrue(maxSupply >= newTotalSupply, TOKEN_MAX_SUPPLY_REACHED, () ->
					"Cannot mint new supply (" + amount + "). Max supply (" + maxSupply + ") reached");
		}

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

	public void initSupplyConstraints(TokenSupplyType supplyType, long maxSupply) {
		this.supplyType = supplyType;
		this.maxSupply = maxSupply;
	}

	public long getMaxSupply() {
		return maxSupply;
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

	public void setType(TokenType type) {
		this.type = type;
	}

	public TokenType getType() {
		return type;
	}

	public void setLastUsedSerialNumber(long lastUsedSerialNumber) {
		this.lastUsedSerialNumber = lastUsedSerialNumber;
	}

	public long getLastUsedSerialNumber() {
		return lastUsedSerialNumber;
	}

	public boolean hasMintedUniqueTokens() {
		return !mintedUniqueTokens.isEmpty();
	}

	public boolean hasBurnedUniqueTokens() { return !burnedUniqueTokens.isEmpty(); }

	public List<UniqueToken> burnedUniqueTokens() { return burnedUniqueTokens; }

	public List<UniqueToken> mintedUniqueTokens() {
		return mintedUniqueTokens;
	}

	public JKey getFeeScheduleKey() {
		return feeScheduleKey;
	}

	public boolean hasFeeScheduleKey() {
		return feeScheduleKey != null;
	}

	public void setFeeScheduleKey(final JKey feeScheduleKey) {
		this.feeScheduleKey = feeScheduleKey;
	}

	public List<FcCustomFee> getFeeSchedule() {
		return feeSchedule;
	}

	public void setFeeSchedule(final List<FcCustomFee> feeSchedule) {
		this.feeSchedule = feeSchedule;
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
				.add("feeScheduleKey", describe(feeScheduleKey))
				.add("feeSchedule", feeSchedule)
				.toString();
	}
}
