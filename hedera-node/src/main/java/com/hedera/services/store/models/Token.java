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
import static com.hedera.services.state.merkle.MerkleUniqueTokenId.MAX_NUM_ALLOWED;
import static com.hedera.services.utils.MiscUtils.describe;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DOES_NOT_OWN_WIPED_NFT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_METADATA;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_METADATA;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPING_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SERIAL_NUMBER_LIMIT_REACHED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_MAX_SUPPLY_REACHED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TREASURY_MUST_OWN_BURNED_NFT;

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
    private JKey adminKey;
    private boolean frozenByDefault;
	private Account treasury;
	private Account autoRenewAccount;
	private boolean deleted;
	private boolean autoRemoved = false;
	private long expiry;

	private long lastUsedSerialNumber;

	public Token(Id id) {
		this.id = id;
	}

	public void mint(final TokenRelationship treasuryRel, final long amount) {
		validateTrue(amount > 0, INVALID_TOKEN_MINT_AMOUNT, errorMessage("mint", amount, treasuryRel));
		validateTrue(type == TokenType.FUNGIBLE_COMMON, FAIL_INVALID,
				"Fungible mint can be invoked only on Fungible token type");

		changeSupply(treasuryRel, +amount, INVALID_TOKEN_MINT_AMOUNT);
	}

	/**
	 * Minting unique tokens creates new instances of the given base unique token.
	 * Increments the serial number of the given base unique token, and assigns each of the numbers to each new unique
	 * token instance.
	 *
	 * @param ownershipTracker
	 * 		- a tracker of changes made to the ownership of the tokens
	 * @param treasuryRel
	 * 		- the relationship between the treasury account and the token
	 * @param metadata
	 * 		- a list of user-defined metadata, related to the nft instances.
	 * @param creationTime
	 * 		- the consensus time of the token mint transaction
	 */
	public void mint(
			final OwnershipTracker ownershipTracker,
			final TokenRelationship treasuryRel,
			final List<ByteString> metadata,
			final RichInstant creationTime
	) {
		final var metadataCount = metadata.size();
		validateFalse(metadata.isEmpty(), INVALID_TOKEN_MINT_METADATA,
				"Cannot mint zero unique tokens");
		validateTrue(type == TokenType.NON_FUNGIBLE_UNIQUE, FAIL_INVALID,
				"Non-fungible mint can be invoked only on non-fungible token type");
		validateTrue((lastUsedSerialNumber + metadataCount) <= MAX_NUM_ALLOWED, SERIAL_NUMBER_LIMIT_REACHED);

		changeSupply(treasuryRel, metadataCount, FAIL_INVALID);

		for (ByteString m : metadata) {
			lastUsedSerialNumber++;
			// The default sentinel account is used (0.0.0) to represent unique tokens owned by the Treasury
			final var uniqueToken = new UniqueToken(id, lastUsedSerialNumber, creationTime, Id.DEFAULT, m.toByteArray());
			mintedUniqueTokens.add(uniqueToken);
			ownershipTracker.add(id, OwnershipTracker.forMinting(treasury.getId(), lastUsedSerialNumber));
		}
		treasury.setOwnedNfts(treasury.getOwnedNfts() + metadataCount);
	}

	public void burn(final TokenRelationship treasuryRel, final long amount) {
		validateTrue(amount > 0, INVALID_TOKEN_BURN_AMOUNT, errorMessage("burn", amount, treasuryRel));
		changeSupply(treasuryRel, -amount, INVALID_TOKEN_BURN_AMOUNT);
	}

	/**
	 * Burning unique tokens effectively destroys them, as well as reduces the total supply of the token.
	 *
	 * @param ownershipTracker
	 * 		- a tracker of changes made to the nft ownership
	 * @param treasuryRelationship
	 * 		- the relationship between the treasury account and the token
	 * @param serialNumbers
	 * 		- the serial numbers, representing the unique tokens which will be destroyed.
	 */
	public void burn(
			final OwnershipTracker ownershipTracker,
			final TokenRelationship treasuryRelationship,
			final List<Long> serialNumbers
	) {
		validateTrue(type == TokenType.NON_FUNGIBLE_UNIQUE, FAIL_INVALID);
		validateFalse(serialNumbers.isEmpty(), INVALID_TOKEN_BURN_METADATA);
		final var treasuryId = treasury.getId();
		for (final long serialNum : serialNumbers) {
			final var uniqueToken = loadedUniqueTokens.get(serialNum);
			validateTrue(uniqueToken != null, FAIL_INVALID);

			final var treasuryIsOwner = uniqueToken.getOwner().equals(Id.DEFAULT);
			validateTrue(treasuryIsOwner, TREASURY_MUST_OWN_BURNED_NFT);
			ownershipTracker.add(id, OwnershipTracker.forRemoving(treasuryId, serialNum));
			removedUniqueTokens.add(new UniqueToken(id, serialNum, treasuryId));
		}
		final var numBurned = serialNumbers.size();
		treasury.setOwnedNfts(treasury.getOwnedNfts() - numBurned);
		changeSupply(treasuryRelationship, -numBurned, FAIL_INVALID);
	}

	/**
	 * Wiping fungible tokens removes the balance of the given account, as well as reduces the total supply.
	 *
	 * @param accountRel
	 * 		- the relationship between the account which owns the tokens and the token
	 * @param amount
	 * 		- amount to be wiped
	 */
	public void wipe(final TokenRelationship accountRel, final long amount) {
		validateTrue(type == TokenType.FUNGIBLE_COMMON, FAIL_INVALID,
				"Fungible wipe can be invoked only on Fungible token type.");

		baseWipeValidations(accountRel);
		amountWipeValidations(accountRel, amount);
		final var newTotalSupply = totalSupply - amount;
		final var newAccBalance = accountRel.getBalance() - amount;

		accountRel.setBalance(newAccBalance);
		setTotalSupply(newTotalSupply);
	}

	/**
	 * Wiping unique tokens removes the unique token instances, associated to the given account, as well as reduces the
	 * total supply.
	 *
	 * @param ownershipTracker
	 * 		- a tracker of changes made to the ownership of the tokens
	 * @param accountRel
	 * 		- the relationship between the account, which owns the tokens, and the token
	 * @param serialNumbers
	 * 		- a list of serial numbers, representing the tokens to be wiped
	 */
	public void wipe(OwnershipTracker ownershipTracker, TokenRelationship accountRel, List<Long> serialNumbers) {
		validateTrue(type == TokenType.NON_FUNGIBLE_UNIQUE, FAIL_INVALID);
		validateFalse(serialNumbers.isEmpty(), FAIL_INVALID);

		for (var serialNum : serialNumbers) {
			final var uniqueToken = loadedUniqueTokens.get(serialNum);
			validateTrue(uniqueToken != null, FAIL_INVALID);
			final var wipeAccountIsOwner = uniqueToken.getOwner().equals(accountRel.getAccount().getId());
			validateTrue(wipeAccountIsOwner, ACCOUNT_DOES_NOT_OWN_WIPED_NFT);
		}

		baseWipeValidations(accountRel);
		final var newTotalSupply = totalSupply - serialNumbers.size();
		final var newAccountBalance = accountRel.getBalance() - serialNumbers.size();
		final var account = accountRel.getAccount();
		for (long serialNum : serialNumbers) {
			ownershipTracker.add(id, OwnershipTracker.forRemoving(account.getId(), serialNum));
			removedUniqueTokens.add(new UniqueToken(id, serialNum, account.getId()));
		}

		account.setOwnedNfts(account.getOwnedNfts() - serialNumbers.size());
		accountRel.setBalance(newAccountBalance);
		setTotalSupply(newTotalSupply);
	}
	/**
	 * Performs a check if the target token has an admin key.
	 * If the admin key is not present throws an exception and does not mutate the token.
	 * If the admin key is present, marks it as deleted.
	 */
    public void delete() {
        validateTrue(hasAdminKey(), TOKEN_IS_IMMUTABLE);

        setIsDeleted(true);
    }

    public boolean hasAdminKey() {
        return adminKey != null;
    }

	public TokenRelationship newRelationshipWith(final Account account) {
		final var newRel = new TokenRelationship(this, account);
		if (hasFreezeKey() && frozenByDefault) {
			newRel.setFrozen(true);
		}
		newRel.setKycGranted(!hasKycKey());
		return newRel;
	}

	private void changeSupply(TokenRelationship treasuryRel, long amount, ResponseCodeEnum negSupplyCode) {
		validateTrue(treasuryRel != null, FAIL_INVALID,
				"Cannot mint with a null treasuryRel");
		validateTrue(treasuryRel.hasInvolvedIds(id, treasury.getId()), FAIL_INVALID,
				"Cannot change " + this + " supply (" + amount + ") with non-treasury rel " + treasuryRel);

		validateTrue(supplyKey != null, TOKEN_HAS_NO_SUPPLY_KEY);

		final long newTotalSupply = totalSupply + amount;
		validateTrue(newTotalSupply >= 0, negSupplyCode);

		if (supplyType == TokenSupplyType.FINITE) {
			validateTrue(maxSupply >= newTotalSupply, TOKEN_MAX_SUPPLY_REACHED,
					"Cannot mint new supply (" + amount + "). Max supply (" + maxSupply + ") reached");
		}

		final long newTreasuryBalance = treasuryRel.getBalance() + amount;
		validateTrue(newTreasuryBalance >= 0, INSUFFICIENT_TOKEN_BALANCE);

		setTotalSupply(newTotalSupply);
		treasuryRel.setBalance(newTreasuryBalance);
	}

	private void baseWipeValidations(final TokenRelationship accountRel) {
		validateTrue(hasWipeKey(), TOKEN_HAS_NO_WIPE_KEY,
				"Cannot wipe Tokens without wipe key.");

		validateFalse(treasury.getId().equals(accountRel.getAccount().getId()), CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT,
				"Cannot wipe treasury account of token.");
	}

	private void amountWipeValidations(final TokenRelationship accountRel, final long amount) {
		validateTrue(amount > 0, INVALID_WIPING_AMOUNT, errorMessage("wipe", amount, accountRel));

		final var newTotalSupply = totalSupply - amount;
		validateTrue(newTotalSupply >= 0, INVALID_WIPING_AMOUNT,
				"Wiping would negate the total supply of the given token.");

		final var newAccountBalance = accountRel.getBalance() - amount;
		validateTrue(newAccountBalance >= 0, INVALID_WIPING_AMOUNT,
				"Wiping would negate account balance");
	}

	private String errorMessage(final String op, final long amount, final TokenRelationship rel) {
		return "Cannot " + op + " " + amount + " units of " + this + " from " + rel;
	}

	public Account getTreasury() {
		return treasury;
	}

	public void setTreasury(final Account treasury) {
		this.treasury = treasury;
	}

	public Account getAutoRenewAccount() {
		return autoRenewAccount;
	}

	public void setAutoRenewAccount(final Account autoRenewAccount) {
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

	public void setSupplyKey(final JKey supplyKey) {
		this.supplyKey = supplyKey;
	}

	public void setKycKey(final JKey kycKey) {
		this.kycKey = kycKey;
	}

	public void setFreezeKey(final JKey freezeKey) {
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

	public void setWipeKey(final JKey wipeKey) {
		this.wipeKey = wipeKey;
	}

    public JKey getAdminKey() {
        return adminKey;
    }

    public void setAdminKey(final JKey adminKey) {
        this.adminKey = adminKey;
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

	public boolean isDeleted() {
		return deleted;
	}

	public void setIsDeleted(final boolean deleted) {
		this.deleted = deleted;
	}

	public long getExpiry() {
		return expiry;
	}

	public void setExpiry(final long expiry) {
		this.expiry = expiry;
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

	public void setLoadedUniqueTokens(final Map<Long, UniqueToken> loadedUniqueTokens) {
		this.loadedUniqueTokens = loadedUniqueTokens;
	}

	public boolean isBelievedToHaveBeenAutoRemoved() {
		return autoRemoved;
	}

	public void markAutoRemoved() {
		this.autoRemoved = true;
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
				.add("deleted", deleted)
				.add("autoRemoved", autoRemoved)
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
