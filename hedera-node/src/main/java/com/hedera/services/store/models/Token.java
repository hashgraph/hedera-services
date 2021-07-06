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
import com.hedera.services.state.submerkle.RichInstant;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.utils.MiscUtils.describe;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPING_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
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
	private final List<UniqueToken> mintedUniqueTokens = new ArrayList<>();
	private final List<UniqueToken> removedUniqueTokens = new ArrayList<>();
	private Map<Long, UniqueToken> loadedUniqueTokens = new HashMap<>();
	private boolean supplyHasChanged;
	private TokenType type;
	private TokenSupplyType supplyType;
	private long totalSupply;
	private long maxSupply;
	private JKey kycKey;
	private JKey freezeKey;
	private JKey supplyKey;
	private JKey wipeKey;
	private boolean frozenByDefault;
	private Account treasury;
	private Account autoRenewAccount;

	private long lastUsedSerialNumber;

	public Token(Id id) {
		this.id = id;
	}

	public void mint(TokenRelationship treasuryRel, long amount) {
		validateTrue(amount > 0, FAIL_INVALID, () ->
				"Cannot mint " + amount + " units of " + this + " from " + treasuryRel);
		validateTrue(type == TokenType.FUNGIBLE_COMMON, FAIL_INVALID, () ->
				"Fungible mint can be invoked only on Fungible token type");

		changeSupply(treasuryRel, +amount, INVALID_TOKEN_MINT_AMOUNT);
	}

	/**
	 * Minting unique tokens creates new instances of the given base unique token.
	 * Increments the serial number of the given base unique token, and assigns each of the numbers to each new unique token instance.
	 * @param ownershipTracker - a tracker of changes made to the ownership of the tokens
	 * @param treasuryRel - the relationship between the treasury account and the token
	 * @param metadata - a list of user-defined metadata, related to the nft instances.
	 * @param creationTime - the consensus time of the token mint transaction
	 */
	public void mint(
			final OwnershipTracker ownershipTracker,
			final TokenRelationship treasuryRel,
			final List<ByteString> metadata,
			final RichInstant creationTime) {
		validateTrue(metadata.size() > 0, FAIL_INVALID, () ->
				"Cannot mint " + metadata.size() + " numbers of Unique Tokens");
		validateTrue(type == TokenType.NON_FUNGIBLE_UNIQUE, FAIL_INVALID, () ->
				"Non fungible mint can be invoked only on Non fungible token type");

		changeSupply(treasuryRel, metadata.size(), FAIL_INVALID);

		for (ByteString m : metadata) {
			lastUsedSerialNumber++;
			var uniqueToken = new UniqueToken(id, lastUsedSerialNumber, creationTime, treasury.getId(), m.toByteArray());
			mintedUniqueTokens.add(uniqueToken);
			ownershipTracker.add(id, OwnershipTracker.forMinting(treasury.getId(), lastUsedSerialNumber));
		}
		treasury.setOwnedNfts(treasury.getOwnedNfts() + metadata.size());
	}

	public void burn(TokenRelationship treasuryRel, long amount) {
		validateTrue(amount > 0, FAIL_INVALID, () ->
				"Cannot burn " + amount + " units of " + this + " from " + treasuryRel);
		changeSupply(treasuryRel, -amount, INVALID_TOKEN_BURN_AMOUNT);
	}

	/**
	 * Burning unique tokens effectively destroys them, as well as reduces the total supply of the token.
	 * @param ownershipTracker - a tracker of changes made to the nft ownership
	 * @param treasuryRelationship - the relationship between the treasury account and the token
	 * @param serialNumbers - the serial numbers, representing the unique tokens which will be destroyed.
	 */
	public void burn(
			final OwnershipTracker ownershipTracker,
			final TokenRelationship treasuryRelationship,
			final List<Long> serialNumbers
	){
		validateTrue( type == TokenType.NON_FUNGIBLE_UNIQUE, FAIL_INVALID, () ->
				"Non fungible burn can be invoked only on Non fungible tokens!");
		validateTrue(serialNumbers.size() > 0, FAIL_INVALID, () ->
				"Non fungible burn cannot be invoked with no serial numbers");
		for (final long serialNum : serialNumbers) {
			ownershipTracker.add(id, OwnershipTracker.forRemoving(id, serialNum));
			removedUniqueTokens.add(new UniqueToken(id, serialNum, treasury.getId()));
		}
		treasury.setOwnedNfts(treasury.getOwnedNfts() - serialNumbers.size());
		changeSupply(treasuryRelationship, -serialNumbers.size(), FAIL_INVALID);
	}

	/**
	 * Wiping fungible tokens removes the balance of the given account, as well as reduces the total supply.
	 * @param accountRel - the relationship between the account which owns the tokens and the token
	 * @param amount - amount to be wiped
	 */
	public void wipe(TokenRelationship accountRel, long amount){
		validateTrue(type == TokenType.FUNGIBLE_COMMON, FAIL_INVALID, () ->
				"Fungible wipe can be invoked only on Fungible token type.");

		baseWipeValidations(accountRel);
		amountWipeValidations(accountRel, amount);
		var newTotalSupply = totalSupply - amount;
		final var newAccBalance  = accountRel.getBalance() - amount;

		accountRel.setBalance(newAccBalance);
		setTotalSupply(newTotalSupply);
	}

	/**
	 * Wiping unique tokens removes the unique token instances, associated to the given account, as well as reduces the total supply.
	 * @param ownershipTracker - a tracker of changes made to the ownership of the tokens
	 * @param accountRel - the relationship between the account, which owns the tokens, and the token
	 * @param serialNumbers - a list of serial numbers, representing the tokens to be wiped
	 */
	public void wipe(OwnershipTracker ownershipTracker, TokenRelationship accountRel, List<Long> serialNumbers) {
		validateTrue(type == TokenType.NON_FUNGIBLE_UNIQUE, FAIL_INVALID, () ->
				"Non fungible wipe can be invoked only on Non fungible token type.");

		validateTrue(serialNumbers.size() > 0, FAIL_INVALID, () ->
				"Cannot wipe " + serialNumbers.size() + " number of Unique Tokens.");
		for (Long serialNum : serialNumbers) {
			var uniqueToken = loadedUniqueTokens.get(serialNum);
			validateTrue(uniqueToken.getOwner().equals(accountRel.getAccount().getId()), FAIL_INVALID, () ->
					"Cannot wipe tokens which given account does not own.");
		}
		baseWipeValidations(accountRel);
		amountWipeValidations(accountRel, (long) serialNumbers.size());
		var newTotalSupply = totalSupply - serialNumbers.size();
		final var newAccountBalance = accountRel.getBalance() - serialNumbers.size();
		var account = accountRel.getAccount();
		for (long serialNum : serialNumbers) {
			ownershipTracker.add(id, OwnershipTracker.forRemoving(account.getId(), serialNum));
			removedUniqueTokens.add(new UniqueToken(id, serialNum, account.getId()));
		}

		account.setOwnedNfts(account.getOwnedNfts() - serialNumbers.size());
		accountRel.setBalance(newAccountBalance);
		setTotalSupply(newTotalSupply);
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

	private void baseWipeValidations(TokenRelationship accountRel) {
		validateTrue(hasWipeKey(), TOKEN_HAS_NO_WIPE_KEY, () ->
				"Cannot wipe Tokens without wipe key.");

		validateFalse(treasury.getId().equals(accountRel.getAccount().getId()), CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT, ()->
				"Cannot wipe treasury account of token.");
	}

	private void amountWipeValidations(TokenRelationship accountRel, long amount) {
		validateTrue(amount > 0, INVALID_WIPING_AMOUNT, () ->
				"Cannot wipe " + amount + " units of " + this + " from " + accountRel);

		var newTotalSupply = totalSupply - amount;
		validateTrue( newTotalSupply >= 0, INSUFFICIENT_TOKEN_BALANCE, () ->
				"Wiping would negate the total supply of the given token.");

		final var newAccountBalance = accountRel.getBalance() - amount;
		validateTrue(newAccountBalance >= 0, INSUFFICIENT_TOKEN_BALANCE, ()->
				"Wiping would negate account balance");
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

	private boolean hasWipeKey() {
		return wipeKey != null;
	}

	public JKey getWipeKey() {
		return wipeKey;
	}

	public void setWipeKey(JKey wipeKey) {
		this.wipeKey = wipeKey;
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

	public List<UniqueToken> mintedUniqueTokens() {
		return mintedUniqueTokens;
	}

	public boolean hasRemovedUniqueTokens() {
		return !removedUniqueTokens.isEmpty();
	}

	public List<UniqueToken> removedUniqueTokens() {
		return removedUniqueTokens;
	}

	public Map<Long, UniqueToken> getLoadedUniqueTokens() {
		return loadedUniqueTokens;
	}

	public void setLoadedUniqueTokens(Map<Long, UniqueToken> loadedUniqueTokens) {
		this.loadedUniqueTokens = loadedUniqueTokens;
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
				.add("type", type)
				.add("treasury", treasury)
				.add("autoRenewAccount", autoRenewAccount)
				.add("kycKey", describe(kycKey))
				.add("freezeKey", describe(freezeKey))
				.add("frozenByDefault", frozenByDefault)
				.add("supplyKey", describe(supplyKey))
				.add("currentSerialNumber", lastUsedSerialNumber)
				.toString();
	}
}
